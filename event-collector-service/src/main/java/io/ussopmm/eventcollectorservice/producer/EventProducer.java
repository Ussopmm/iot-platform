package io.ussopmm.eventcollectorservice.producer;

import io.opentelemetry.instrumentation.annotations.WithSpan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${spring.kafka.first-topic.name}")
    private String topic;

    @WithSpan("eventProducer.sendEvent")
    public CompletableFuture<RecordMetadata> sendEvent(String deviceId) {
        return kafkaTemplate.send(topic, deviceId)
                .thenApply(SendResult::getRecordMetadata)
                .exceptionally(ex -> {
                    log.error("Error sending event", ex);
                    throw new CompletionException(ex);
                });
    }
}
