package io.ussopmm.device_collector_service.listener;

import com.nashkod.avro.Device;
import io.ussopmm.device_collector_service.helpers.ShardMetrics;
import io.ussopmm.device_collector_service.service.DeviceService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.SendResult;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;

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
        List<ConsumerRecord<String, Device>> batch = List.of(record("dev-10"), record("dev-11"));

        when(deviceService.save(anyList(), eq(metrics)))
                .thenReturn(Mono.error(new IllegalArgumentException("bad payload")));

        when(kafkaTemplate.send(Mockito.<ProducerRecord<String, Device>>any()))
                .thenAnswer(inv -> {
                    ProducerRecord<String, Device> pr = inv.getArgument(0);
                    return completedSendFor(pr);
                });

        listener.listen(batch, ack);

        // Капторим именно ProducerRecord, не RecordMetadata
        ArgumentCaptor<ProducerRecord<String, Device>> prCaptor =
                ArgumentCaptor.forClass((Class) ProducerRecord.class);

        await().atMost(5, SECONDS).untilAsserted(() -> {
            // ack был вызван (после отправки в DLT)
            verify(ack, times(1)).acknowledge();
            // отправили оба сообщения
            verify(kafkaTemplate, times(2)).send(prCaptor.capture());
        });

        List<ProducerRecord<String, Device>> sent = prCaptor.getAllValues();
        assertThat(sent).hasSize(2);
        for (ProducerRecord<String, Device> pr : sent) {
            assertThat(pr.topic()).isEqualTo("device-topic.dlt");
            assertThat(pr.value()).isNotNull();
            Headers h = pr.headers();
            assertThat(h.lastHeader("kafka_dlt-exception-fqcn")).isNotNull();
            assertThat(h.lastHeader("kafka_dlt-exception-message")).isNotNull();
            assertThat(h.lastHeader("kafka_dlt-original-topic")).isNotNull();
            assertThat(h.lastHeader("kafka_dlt-original-partition")).isNotNull();
            assertThat(h.lastHeader("kafka_dlt-original-offset")).isNotNull();
            assertThat(h.lastHeader("kafka_dlt-original-timestamp")).isNotNull();
            // перенос пользовательского заголовка
            assertThat(h.lastHeader("x-correlation-id")).isNotNull();
        }

        verify(metrics, atLeastOnce()).incError(anyString(), eq("VALIDATION"));
    }


    private static CompletableFuture<SendResult<String, Device>> completedSendFor(
            ProducerRecord<String, Device> pr
    ) {
        int part = pr.partition() == null ? 0 : pr.partition();
        RecordMetadata md = new RecordMetadata(
                new TopicPartition(pr.topic(), part),
                0L, 0L, System.currentTimeMillis(),
                0L, 0, 0
        );
        // ВАЖНО: сначала ProducerRecord, потом RecordMetadata
        return CompletableFuture.completedFuture(new SendResult<>(pr, md));
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
