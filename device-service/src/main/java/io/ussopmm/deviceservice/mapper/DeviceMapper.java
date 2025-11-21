package io.ussopmm.deviceservice.mapper;

import com.nashkod.avro.Device;
import io.ussopmm.deviceservice.entity.DeviceEntity;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;

import static org.mapstruct.MappingConstants.ComponentModel.SPRING;

@Mapper(componentModel = SPRING, injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface DeviceMapper {

    io.ussopmm.device.dto.DeviceResponse from(DeviceEntity entity);
    DeviceEntity from(io.ussopmm.device.dto.DeviceResponse dto);
    DeviceEntity from(io.ussopmm.device.dto.CreateDeviceRequest dto);
}
