package io.ussopmm.device_collector_service.repository;

import io.ussopmm.device_collector_service.entity.DeviceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeviceRepository extends JpaRepository<DeviceEntity,String> {
}
