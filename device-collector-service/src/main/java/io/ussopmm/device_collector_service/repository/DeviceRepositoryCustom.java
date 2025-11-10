package io.ussopmm.device_collector_service.repository;

import io.ussopmm.device_collector_service.entity.DeviceEntity;
import io.ussopmm.device_collector_service.helpers.ShardMetrics;

import java.util.List;

public interface DeviceRepositoryCustom {
    long upsertBatch(List<DeviceEntity> batch, ShardMetrics metrics);
    int upsertOne(DeviceEntity d, ShardMetrics metrics);
}
