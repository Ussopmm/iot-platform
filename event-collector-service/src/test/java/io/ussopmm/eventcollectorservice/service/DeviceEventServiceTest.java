package io.ussopmm.eventcollectorservice.service;

import io.ussopmm.eventcollectorservice.entity.DeviceEventEntity;
import io.ussopmm.eventcollectorservice.producer.EventProducer;
import io.ussopmm.eventcollectorservice.repository.DeviceEventRepository;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceEventServiceTest {

    @Mock
    DeviceEventRepository repository;

    @Mock
    EventProducer eventProducer;

    @InjectMocks
    DeviceEventService deviceEventService;

    private static DeviceEventEntity entity(String deviceId) {
        return DeviceEventEntity.builder()
                .key(DeviceEventEntity.Key.builder()
                        .deviceId(deviceId)
                        .eventId("evt-1")
                        .timestamp(123L)
                        .build())
                .type("TYPE")
                .payload("{}")
                .build();
    }

    @Test
    void save_newDevice_publishesAndSaves() {
        //given
        var e = entity("dev-1");
        when(eventProducer.sendEvent("dev-1"))
                .thenReturn(completedFuture(mock(RecordMetadata.class)));
        //when
        deviceEventService.save(e);

        //then
        verify(eventProducer).sendEvent("dev-1");
        verify(repository, timeout(500)).save(e);
        verifyNoMoreInteractions(eventProducer, repository);
    }

    @Test
    void save_newDevice_producerFails_doesNotSave_andStageIsExceptional() {
        // given
        var e = entity("dev-1");
        when(eventProducer.sendEvent("dev-1"))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("kafka down")));

        // when
        CompletionStage<Void> stage = deviceEventService.save(e);

        // then: the joined call throws; repository.save is never invoked
        assertThatThrownBy(() -> stage.toCompletableFuture().join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(RuntimeException.class)
                .hasMessageContaining("kafka down");

        verify(eventProducer).sendEvent("dev-1");
        verify(repository, never()).save(any());
        verifyNoMoreInteractions(eventProducer, repository);
    }

    @Test
    void save_existingDevice_doesNotPublishButStillSaves() {
        // given
        var e = entity("dev-1");

        when(eventProducer.sendEvent("dev-1"))
                .thenReturn(completedFuture(mock(RecordMetadata.class)));
        deviceEventService.save(e).toCompletableFuture().join();
        reset(eventProducer, repository);
        when(repository.save(any(DeviceEventEntity.class))).thenReturn(e);
        // when
        var stage = deviceEventService.save(e);

        // then
        stage.toCompletableFuture().join();
        verify(eventProducer, never()).sendEvent(anyString());
        verify(repository).save(e);
        verifyNoMoreInteractions(eventProducer, repository);
    }

    @Test
    void shouldRemoveFromSeenDevicesWhenDBSaveFails() throws NoSuchFieldException, IllegalAccessException {
        // given
        var event = entity("dev-1");
        String deviceId = event.getKey().getDeviceId();

        when(eventProducer.sendEvent("dev-1"))
                .thenReturn(completedFuture(null));
        when(repository.save(any(DeviceEventEntity.class)))
                .thenThrow(new RuntimeException("DB error"));
        // when
        CompletionStage<Void> stage = deviceEventService.save(event);

        // then
        assertThatThrownBy(() -> stage.toCompletableFuture().join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(RuntimeException.class)
                .hasMessageContaining("DB error");

        verify(eventProducer).sendEvent(deviceId);
        verify(repository).save(event);
        // доступ к приватному полю seenDevices через рефлексию и проверка, что deviceId удалён
        java.lang.reflect.Field f = DeviceEventService.class.getDeclaredField("seenDevices");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Set<String> seenDevices = (java.util.Set<String>) f.get(deviceEventService);
        assertThat(seenDevices).doesNotContain(deviceId);
        verifyNoMoreInteractions(eventProducer, repository);

    }



}
