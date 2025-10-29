package io.ussopmm.eventcollectorservice.listener;

import com.nashkod.avro.DeviceEvent;
import io.ussopmm.eventcollectorservice.entity.DeviceEventEntity;
import io.ussopmm.eventcollectorservice.service.DeviceEventService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventListenerTest {

    @Mock
    DeviceEventService deviceEventService;

    @InjectMocks
    EventListener eventListener;


    @Test
    void listen_ShouldGetEventsAndSaveEachInDatabase() {
        // given: one event in the batch
        DeviceEvent ev1 = mock(DeviceEvent.class);
        when(ev1.getDeviceId()).thenReturn("dev-1");
        when(ev1.getEventId()).thenReturn("evt-42");
        when(ev1.getTimestamp()).thenReturn(1729690000000L);
        when(ev1.getType()).thenReturn("TEMPERATURE");
        when(ev1.getPayload()).thenReturn("{\"c\":24.5}");

        List<DeviceEvent> batch = List.of(ev1);

        when(deviceEventService.save(any(DeviceEventEntity.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        ArgumentCaptor<DeviceEventEntity> captor = ArgumentCaptor.forClass(DeviceEventEntity.class);

        // when
        eventListener.listen(batch);

        // then: called once, with correct payload
        verify(deviceEventService, times(1)).save(captor.capture());
        verifyNoMoreInteractions(deviceEventService);

        DeviceEventEntity saved = captor.getValue();
        assertThat(saved.getKey().getDeviceId()).isEqualTo("dev-1");
        assertThat(saved.getKey().getEventId()).isEqualTo("evt-42");
        assertThat(saved.getKey().getTimestamp()).isEqualTo(1729690000000L);
        assertThat(saved.getType()).isEqualTo("TEMPERATURE");
        assertThat(saved.getPayload()).isEqualTo("{\"c\":24.5}");
    }

    @Test
    void listen_ShouldPersistAllItems_WhenBatchHasMultipleEvents() {
        // given: two events in the batch
        DeviceEvent ev1 = mock(DeviceEvent.class);
        when(ev1.getDeviceId()).thenReturn("dev-1");
        when(ev1.getEventId()).thenReturn("evt-1");
        when(ev1.getTimestamp()).thenReturn(1000L);
        when(ev1.getType()).thenReturn("A");
        when(ev1.getPayload()).thenReturn("p1");

        DeviceEvent ev2 = mock(DeviceEvent.class);
        when(ev2.getDeviceId()).thenReturn("dev-2");
        when(ev2.getEventId()).thenReturn("evt-2");
        when(ev2.getTimestamp()).thenReturn(2000L);
        when(ev2.getType()).thenReturn("B");
        when(ev2.getPayload()).thenReturn("p2");

        List<DeviceEvent> batch = List.of(ev1, ev2);

        when(deviceEventService.save(any(DeviceEventEntity.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        ArgumentCaptor<DeviceEventEntity> captor = ArgumentCaptor.forClass(DeviceEventEntity.class);

        // when
        eventListener.listen(batch);

        // then: called for each event
        verify(deviceEventService, times(2)).save(captor.capture());
        verifyNoMoreInteractions(deviceEventService);

        var savedAll = captor.getAllValues();
        assertThat(savedAll).hasSize(2);

        DeviceEventEntity s1 = savedAll.get(0);
        assertThat(s1.getKey().getDeviceId()).isEqualTo("dev-1");
        assertThat(s1.getKey().getEventId()).isEqualTo("evt-1");
        assertThat(s1.getKey().getTimestamp()).isEqualTo(1000L);
        assertThat(s1.getType()).isEqualTo("A");
        assertThat(s1.getPayload()).isEqualTo("p1");

        DeviceEventEntity s2 = savedAll.get(1);
        assertThat(s2.getKey().getDeviceId()).isEqualTo("dev-2");
        assertThat(s2.getKey().getEventId()).isEqualTo("evt-2");
        assertThat(s2.getKey().getTimestamp()).isEqualTo(2000L);
        assertThat(s2.getType()).isEqualTo("B");
        assertThat(s2.getPayload()).isEqualTo("p2");
    }
}