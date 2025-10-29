package io.ussopmm.eventcollectorservice.repository;

import io.ussopmm.eventcollectorservice.entity.DeviceEventEntity;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeviceEventRepository extends CassandraRepository<DeviceEventEntity, String> {
}
