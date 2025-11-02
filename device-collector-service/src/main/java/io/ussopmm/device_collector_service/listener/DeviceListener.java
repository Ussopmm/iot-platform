package io.ussopmm.device_collector_service.listener;

import com.nashkod.avro.Device;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceListener {

    @WithSpan("deviceListener.listen")
    @KafkaListener(
            topics = "${spring.kafka.second-topic.name}",
            groupId = "${spring.kafka.consumer.group-id}",
            batch = "true",
            containerFactory = "kafkaConsumerContainerFactory"
    )
    public void listen(List<Device> devices) {
//         TODO
    }
}
