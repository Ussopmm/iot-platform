package io.ussopmm.device_collector_service.listener;

import com.nashkod.avro.Device;
import io.ussopmm.device_collector_service.helpers.ShardMetrics;
import io.ussopmm.device_collector_service.service.DeviceService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class DeviceListenerTest {

    @Mock
    KafkaTemplate<String, Device> kafkaTemplate;
    @Mock
    DeviceService deviceService;
    @Mock
    ShardMetrics metrics;
    @Mock
    Acknowledgment ack;

    DeviceListener listener;

    @BeforeEach
    void setUp() throws Exception {
        listener = new DeviceListener(deviceService, kafkaTemplate, metrics);
        var f = DeviceListener.class.getDeclaredField("dltTopic");
        f.setAccessible(true);
        f.set(listener, "device-topic.dlt");
    }


    @Test
    void listen_whenSaveSucceeds_shouldAck() {
        // given
        List<ConsumerRecord<String, Device>> batch = List.of(record("dev-1"), record("dev-2"));

        when(deviceService.save(anyList(), eq(metrics))).thenReturn(Mono.just(2L));

        // when
        listener.listen(batch, ack);

        // then (listen() сам подписывается; ждём сайд-эффекты)
        await().atMost(3, SECONDS).untilAsserted(() ->
                verify(ack, times(1)).acknowledge()
        );

        // в этом кейсе в DLT ничего не уходит
        verifyNoInteractions(kafkaTemplate);
    }


    @Test
    void listen_whenNonTransientError_shouldSendAllToDLT_andAck_andIncMetricWithCode() {
        // given: ошибка валидации => isTransient=false => без ретрая сразу DLT
        List<ConsumerRecord<String, Device>> batch = List.of(record("dev-10"), record("dev-11"));
        when(deviceService.save(anyList(), eq(metrics)))
                .thenReturn(Mono.error(new IllegalArgumentException("bad payload")));

        // чтобы .send(...) успешно завершался
        when(kafkaTemplate.send(any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        // when
        listener.listen(batch, ack);

        // then
        await().atMost(3, SECONDS).untilAsserted(() -> {
            // ack сделан
            verify(ack, times(1)).acknowledge();
            // оба сообщения отправлены в DLT
            verify(kafkaTemplate, times(2)).send(argThat(pr -> {
                assertThat(pr.topic()).isEqualTo("devices.dlt");
                assertThat(pr.value()).isNotNull();
                // оригинальные заголовки должны сохраниться + диагностические добавлены
                assertThat(pr.headers().lastHeader("kafka_dlt-exception-fqcn")).isNotNull();
                assertThat(pr.headers().lastHeader("kafka_dlt-exception-message")).isNotNull();
                assertThat(pr.headers().lastHeader("kafka_dlt-original-topic")).isNotNull();
                assertThat(pr.headers().lastHeader("kafka_dlt-original-partition")).isNotNull();
                assertThat(pr.headers().lastHeader("kafka_dlt-original-offset")).isNotNull();
                assertThat(pr.headers().lastHeader("kafka_dlt-original-timestamp")).isNotNull();
                // проверим что один из наших пользовательских заголовков перенесён
                assertThat(pr.headers().lastHeader("x-correlation-id")).isNotNull();
                return true;
            }));
            // метрика ошибки с кодом VALIDATION хотя бы один раз инкрементирована
            verify(metrics, atLeastOnce()).incError(anyString(), eq("VALIDATION"));
        });
    }



    private ConsumerRecord<String, Device> record(String deviceId) {
        Device d = new Device();
        d.setDeviceId(deviceId);
        d.setDeviceType("SENSOR");
        d.setCreatedAt(1);
        d.setMeta("test meta");

        var headers = new RecordHeaders();
        headers.add("x-correlation-id", UUID.randomUUID().toString().getBytes());
        // topic / partition / offset в конструкторе CR
        ConsumerRecord<String, Device> cr = new ConsumerRecord<>(
                "device-topic", 3, 42L, "key-" + deviceId, d
        );
        try {
            var ctor = ConsumerRecord.class.getConstructor(
                    String.class, Integer.class, Long.class, Long.class, Long.class,
                    org.apache.kafka.common.record.TimestampType.class, long.class, int.class, Object.class, Object.class, org.apache.kafka.common.header.Headers.class
            );
        } catch (NoSuchMethodException e) {
            // если нет расширенного конструктора — воспользуемся полем headers
            try {
                var f = ConsumerRecord.class.getDeclaredField("headers");
                f.setAccessible(true);
                f.set(cr, headers);
            } catch (Exception ignore) {}
        }
        return cr;
    }
}