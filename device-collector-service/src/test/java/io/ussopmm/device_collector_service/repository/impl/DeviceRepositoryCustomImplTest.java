package io.ussopmm.device_collector_service.repository.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ussopmm.device_collector_service.entity.DeviceEntity;
import io.ussopmm.device_collector_service.helpers.ShardMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceRepositoryCustomImplTest {
    @Mock
    NamedParameterJdbcTemplate jdbc;
    @Mock
    ShardMetrics metrics;

    DeviceRepositoryCustomImpl repo;

    @BeforeEach
    void setUp() {
        repo = new DeviceRepositoryCustomImpl(jdbc);
    }


    @Test
    void upsertBatch_buildsParams_callsJdbc_andCountsOnlyPositiveRows_andIncrementsMetricsPerEntity() {
        // given: три сущности -> ожидаем три инкремента метрик; counts = {1,0,2} -> вернуть 2 (только >0)
        List<DeviceEntity> batch = List.of(
                entity("dev-1", "SENSOR", 1L, "test1"),
                entity("dev-2", "ACTUATOR", 2L, "test2"),
                entity("dev-3", "SENSOR", 3L, "test3")
        );

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<SqlParameterSource[]> paramsCaptor = ArgumentCaptor.forClass(SqlParameterSource[].class);

        when(jdbc.batchUpdate(sqlCaptor.capture(), paramsCaptor.capture()))
                .thenReturn(new int[]{1, 0, 2});

        // when
        long updated = repo.upsertBatch(batch, metrics);

        // then: вернули только количество положительных обновлений
        assertThat(updated).isEqualTo(2);

        // SQL содержит ожидаемые плейсхолдеры
        String usedSql = sqlCaptor.getValue();
        assertThat(usedSql)
                .contains("INSERT INTO device.devices")
                .contains(":deviceId", ":deviceType", ":createdAt", ":meta")
                .contains("ON CONFLICT");

        // параметры корректно собраны
        SqlParameterSource[] sentParams = paramsCaptor.getValue();
        assertThat(sentParams).hasSize(3);
        assertParam(sentParams[0], "deviceId", "dev-1");
        assertParam(sentParams[0], "deviceType", "SENSOR");
        assertParam(sentParams[0], "createdAt", 1L);
        assertParam(sentParams[0], "meta", "test1");

        assertParam(sentParams[1], "deviceId", "dev-2");
        assertParam(sentParams[2], "deviceId", "dev-3");

        // метрики инкрементированы по кажому элементу с правильным shard (hash % 2)
        verify(metrics, times(3)).incProcessed(anyString());
        for (DeviceEntity e : batch) {
            String shard = String.valueOf(Math.floorMod(e.getDeviceId().hashCode(), 2));
            verify(metrics, atLeastOnce()).incProcessed(eq(shard));
        }
    }

    @Test
    void upsertOne_callsJdbcUpdate_andReturnsRows_andIncrementsMetricsForShard() {
        // given
        DeviceEntity d = entity("dev-1", "SENSOR", 1L, "test1");
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);

        // when
        int rows = repo.upsertOne(d, metrics);

        // then
        assertThat(rows).isEqualTo(1);
        verify(jdbc, times(1)).update(anyString(), any(MapSqlParameterSource.class));

        String shard = String.valueOf(Math.floorMod(d.getDeviceId().hashCode(), 2));
        verify(metrics, times(1)).incProcessed(eq(shard));
    }

    @Test
    void upsertBatch_propagatesJdbcException() {
        List<DeviceEntity> batch = List.of(entity("dev-1", "SENSOR", 1L, "test1"));
        when(jdbc.batchUpdate(anyString(), any(SqlParameterSource[].class)))
                .thenThrow(new RuntimeException("DB down"));

        assertThatThrownBy(() -> repo.upsertBatch(batch, metrics))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("DB down");

        // на падении БД метрики не должны инкрементироваться
        verifyNoInteractions(metrics);
    }

    @Test
    void upsertOne_propagatesJdbcException() {
        DeviceEntity d = entity("dev-x", "S", 1L, "test");
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class)))
                .thenThrow(new RuntimeException("DB down"));

        assertThatThrownBy(() -> repo.upsertOne(d, metrics))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("DB down");

        verifyNoInteractions(metrics);
    }

    // ----------------- helpers -----------------

    private static DeviceEntity entity(String id, String type, Long createdAt, String meta) {
        return DeviceEntity.builder()
                .deviceId(id)
                .deviceType(type)
                .createdAt(createdAt)
                .meta(meta)
                .build();
    }

    private static void assertParam(SqlParameterSource p, String name, Object expected) {
        assertThat(p.hasValue(name)).as("param %s exists", name).isTrue();
        assertThat(p.getValue(name)).isEqualTo(expected);
    }
}
