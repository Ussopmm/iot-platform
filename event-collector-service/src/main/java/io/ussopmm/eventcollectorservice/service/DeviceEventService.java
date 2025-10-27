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

        CompletionStage<RecordMetadata> ackStage = CompletableFuture.completedFuture(null)
                        .thenCompose(v -> {
                            if (!seenDevices.contains(deviceId)) {
                                seenDevices.add(deviceId);
                                return producer.sendEvent(deviceId);
                            } else {
                                return CompletableFuture.completedFuture(null);
                            }
                        });

        return ackStage.thenRun(() -> {
                    repository.save(deviceEvent);
                    log.info("DeviceEvent save success");
                });
        }
}
