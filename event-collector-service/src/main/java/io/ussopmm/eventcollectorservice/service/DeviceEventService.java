package io.ussopmm.eventcollectorservice.service;

import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.ussopmm.eventcollectorservice.entity.DeviceEventEntity;
import io.ussopmm.eventcollectorservice.producer.EventProducer;
import io.ussopmm.eventcollectorservice.repository.DeviceEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceEventService {

    private final DeviceEventRepository repository;
    private final Set<String> seenDevices = new HashSet<>();
    private final EventProducer producer;

    @WithSpan("deviceEventService.save")
    public CompletionStage<Void> save(DeviceEventEntity deviceEvent) {
        String deviceId = deviceEvent.getKey().getDeviceId();

        CompletionStage<Void> ackStage = CompletableFuture.completedFuture(null)
                        .thenCompose(v -> {
                            if (seenDevices.add(deviceId)) {
                                return producer.sendEvent(deviceId).thenApply(md -> null);
                            } else {
                                return CompletableFuture.completedFuture(null);
                            }
                        });

        return ackStage
                .thenApply(v -> repository.save(deviceEvent))
                // log the saved entity
                .thenAccept(saved -> log.info("Device event with id {} saved", deviceId))
                // throw an exception and remove device id from cache
                .exceptionally(t -> {
                    seenDevices.remove(deviceId);
                    log.error("Error saving device", t);
                    throw new CompletionException(t);
                });
    }
}
