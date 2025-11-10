package io.ussopmm.device_collector_service.repository.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ussopmm.device_collector_service.entity.DeviceEntity;
import io.ussopmm.device_collector_service.helpers.ShardMetrics;
import io.ussopmm.device_collector_service.repository.DeviceRepositoryCustom;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class DeviceRepositoryCustomImpl implements DeviceRepositoryCustom {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    private static final String UPSERT_SQL = """
        INSERT INTO device.devices (device_id, device_type, created_at, meta)
        VALUES (:deviceId, :deviceType, :createdAt, CAST(:meta AS jsonb))
        ON CONFLICT (device_id) DO UPDATE
        SET device_type = EXCLUDED.device_type,
            created_at  = EXCLUDED.created_at,
            meta        = EXCLUDED.meta
        """;

    @Override
    public long upsertBatch(List<DeviceEntity> batch, ShardMetrics metrics) {
        var params = batch.stream().map(d -> new MapSqlParameterSource()
                .addValue("deviceId", d.getDeviceId())
                .addValue("deviceType", d.getDeviceType())
                .addValue("createdAt", d.getCreatedAt())
                .addValue("meta", d.getMeta())).toArray(SqlParameterSource[]::new);

        int[] counts = jdbc.batchUpdate(UPSERT_SQL, params);

        batch.forEach(d -> {
            int shardIdx = Math.floorMod(d.getDeviceId().hashCode(), 2);
            try (var _1 = MDC.putCloseable("deviceId", d.getDeviceId());
                 var _2 = MDC.putCloseable("shard", String.valueOf(shardIdx))) {
                metrics.incProcessed(String.valueOf(shardIdx));
            }
        });

        return Arrays.stream(counts).filter(c -> c > 0).count();
    }

    @Override
    public int upsertOne(DeviceEntity d, ShardMetrics metrics) {
        var p = new MapSqlParameterSource()
                .addValue("deviceId", d.getDeviceId())
                .addValue("deviceType", d.getDeviceType())
                .addValue("createdAt", d.getCreatedAt())
                .addValue("meta", d.getMeta());
        int rows = jdbc.update(UPSERT_SQL, p);

        int shardIdx = Math.floorMod(d.getDeviceId().hashCode(), 2);
        try (var _1 = MDC.putCloseable("deviceId", d.getDeviceId());
             var _2 = MDC.putCloseable("shard", String.valueOf(shardIdx))) {
            metrics.incProcessed(String.valueOf(shardIdx));
        }
        return rows;
    }
}
