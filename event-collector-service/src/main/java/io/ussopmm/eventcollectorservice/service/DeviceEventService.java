package io.ussopmm.eventcollectorservice.service;

import com.nashkod.avro.Device;
import com.nashkod.avro.DeviceEvent;
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
    public CompletionStage<Void> save(DeviceEvent deviceEvent) {
        Device device = deviceEvent.getDevice();

        CompletionStage<Void> ackStage = CompletableFuture.completedFuture(null)
                        .thenCompose(v -> {
                            if (seenDevices.add(device.getDeviceId())) {
                                return producer.sendEvent(device).thenApply(md -> null);
                            } else {
                                return CompletableFuture.completedFuture(null);
                            }
                        });

        return ackStage
                .thenApply(v -> repository.save(DeviceEventEntity.builder()
                        .key(DeviceEventEntity.Key.builder()
                                .deviceId(deviceEvent.getDevice().getDeviceId())
                                .eventId(deviceEvent.getEventId())
                                .timestamp(deviceEvent.getTimestamp())
                                .build())
                        .type(deviceEvent.getType())
                        .payload(deviceEvent.getPayload())
                        .build()))
                // log the saved entity
                .thenAccept(saved -> log.info("Device event with id {} saved", deviceEvent.getEventId()))
                // throw an exception and remove device id from cache
                .exceptionally(t -> {
                    seenDevices.remove(device.getDeviceId());
                    log.error("Error saving device", t);
                    throw new CompletionException(t);
                });
    }
}
