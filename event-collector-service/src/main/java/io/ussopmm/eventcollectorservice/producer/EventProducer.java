package io.ussopmm.eventcollectorservice.producer;

import com.nashkod.avro.Device;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventProducer {

    private final KafkaTemplate<String, Device> kafkaTemplate;

    @Value("${spring.kafka.first-topic.name}")
    private String topic;

    @WithSpan("eventProducer.sendEvent")
    public CompletableFuture<RecordMetadata> sendEvent(Device device) {
        return kafkaTemplate.send(topic, device)
                .thenApply(res -> {
                    log.info("Event for device {} sent to topic {}", device.getDeviceId(), topic);
                    return res.getRecordMetadata();
                })
                .exceptionally(ex -> {
                    log.error("Error sending event", ex);
                    throw new CompletionException(ex);
                });
    }
}
