package io.ussopmm.device_collector_service.service;

import com.nashkod.avro.Device;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.ussopmm.device_collector_service.entity.DeviceEntity;
import io.ussopmm.device_collector_service.helpers.ShardMetrics;
import io.ussopmm.device_collector_service.repository.DeviceRepositoryCustom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceRepositoryCustom deviceRepository;

    public Mono<Long> save(List<ConsumerRecord<String, Device>> devices, ShardMetrics metrics) {
        var tracer = GlobalOpenTelemetry.getTracer(DeviceService.class.getSimpleName());
        var saveSpan = tracer.spanBuilder("save-devices").startSpan();

        List<DeviceEntity> entities = devices.stream()
                .map(r -> DeviceEntity.builder()
                        .deviceId(r.value().getDeviceId())
                        .deviceType(r.value().getDeviceType())
                        .createdAt(r.value().getCreatedAt())
                        .meta(r.value().getMeta())
                        .build())
                .toList();

        int batchSize = 50;
        List<List<DeviceEntity>> batches = new ArrayList<>();
        for (int i = 0; i < entities.size(); i += batchSize) {
            batches.add(entities.subList(i, Math.min(i + batchSize, entities.size())));
        }

        return Mono.defer(() -> {
            return Flux.fromIterable(batches)
                    .flatMap(batch -> Mono.defer(() -> {
                        var b = tracer.spanBuilder("db.upsert.batch").startSpan();
                        try (var _ = b.makeCurrent()) {
                            return Mono.fromCallable(() -> deviceRepository.upsertBatch(batch, metrics))
                                    .subscribeOn(Schedulers.boundedElastic())
                                    .doFinally(__ -> b.end());
                        }
                    }), 4)
                    .reduce(0L, Long::sum)
                    .doFinally(__ -> saveSpan.end());
        });
    }
}
