package io.ussopmm.eventcollectorservice.listener;

import com.nashkod.avro.Device;
import com.nashkod.avro.DeviceEvent;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.ussopmm.eventcollectorservice.entity.DeviceEventEntity;
import io.ussopmm.eventcollectorservice.service.DeviceEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventListener {

    private final DeviceEventService deviceEventService;

    @WithSpan("eventListener.listen")
    @KafkaListener(topics = "${spring.kafka.second-topic.name}",
            groupId = "${spring.kafka.consumer.group-id}",
            batch = "true",
            containerFactory = "kafkaConsumerContainerFactory"
    )
    public void listen(List<DeviceEvent> deviceEvents) {
        log.info("Event received");
        deviceEvents.forEach(deviceEvent -> {
            deviceEventService.save(deviceEvent)
                    .toCompletableFuture().join();
        });

        log.info("Device Event saved");
    }
}
