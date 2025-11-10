package io.ussopmm.device_collector_service.helpers;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ShardMetrics {

    private final MeterRegistry registry;

    public static final String M_DEVICE_PROCESSED = "device_processed_total"; // counter
    public static final String M_DEVICE_ERRORS    = "device_errors_total";    // counter

    public void incProcessed(String shard) {
        Counter counter = registry.counter(
                M_DEVICE_PROCESSED,
                Tags.of("shard", shard));
        counter.increment();
    }

    public void incError(String shard, String cause) {
        Counter counter = registry.counter(
                M_DEVICE_ERRORS,
                Tags.of("shard", shard, "cause", cause));
        counter.increment();
    }

}
