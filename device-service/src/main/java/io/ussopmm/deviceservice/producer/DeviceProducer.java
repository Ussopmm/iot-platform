package io.ussopmm.deviceservice.producer;

import com.nashkod.avro.Device;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceProducer {

    private final KafkaTemplate<String, Device> kafkaTemplate;


    @Value("${spring.kafka.topic.name}")
    private String topic;

    public Mono<Void> sendEvent(Device device) {
        return Mono.fromRunnable(() -> kafkaTemplate.send(topic, device))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(t -> log.info("Device event sent to topic [{}] for deviceId = [{}]", topic, device.getDeviceId()))
                .then();
    }
}
