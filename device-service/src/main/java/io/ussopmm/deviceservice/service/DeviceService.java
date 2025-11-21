package io.ussopmm.deviceservice.service;

import com.nashkod.avro.Device;
import io.ussopmm.deviceservice.entity.DeviceEntity;
import io.ussopmm.deviceservice.mapper.DeviceMapper;
import io.ussopmm.deviceservice.producer.DeviceProducer;
import io.ussopmm.deviceservice.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final DeviceMapper mapper;
    private final DeviceProducer producer;
    public DeviceEntity getById(String deviceId) {
        return deviceRepository.findById(deviceId).orElse(null);
    }

    public io.ussopmm.device.dto.DeviceResponse get(String deviceId) {
        return mapper.from(getById(deviceId));
    }

    public io.ussopmm.device.dto.DeviceResponse create(io.ussopmm.device.dto.CreateDeviceRequest request) {
        DeviceEntity entity = mapper.from(request);
        entity.setCreatedAt(Instant.now().getEpochSecond());
        entity.setDeviceId(UUID.randomUUID().toString());
        Device device = Device.newBuilder()
                .setDeviceId(entity.getDeviceId())
                .setDeviceType(entity.getDeviceType())
                .setCreatedAt(entity.getCreatedAt())
                .setMeta(entity.getMeta() != null ? entity.getMeta() : "")
                .build();
        producer.sendEvent(device)
                .doFinally(t -> log.info("Device creation event processing finished for deviceId = [{}]", entity.getDeviceId())).subscribe();


    }

}
