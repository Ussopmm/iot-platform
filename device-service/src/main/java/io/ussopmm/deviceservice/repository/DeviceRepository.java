package io.ussopmm.deviceservice.repository;

import io.ussopmm.deviceservice.entity.DeviceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeviceRepository extends JpaRepository<DeviceEntity, String> {
}
