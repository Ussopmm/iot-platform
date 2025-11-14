package io.ussopmm.device_collector_service.it;

import io.micrometer.core.instrument.MeterRegistry;
import io.ussopmm.device_collector_service.entity.DeviceEntity;
import io.ussopmm.device_collector_service.helpers.ShardMetrics;
import io.ussopmm.device_collector_service.repository.DeviceRepositoryCustom;
import org.apache.shardingsphere.infra.hint.HintManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


@ActiveProfiles("test")
@SpringBootTest( properties = {
        // статичные вещи
        "server.port=0",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.show-sql=false",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=never",

        // отрубаем Kafka/Streams/TaskExecution автоконфиги, чтобы контекст не тянул их зря
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration," +
                "org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration",

        "app.kafka.enabled=false",
        "kafka.enabled=false",
        "otel.sdk.disabled=true",
        "management.tracing.enabled=false",
        "spring.kafka.concurrency=1",
        "spring.kafka.poll-timeout=2000",
        "spring.kafka.batch-listener-enabled=true"
})
@Testcontainers
public class DeviceRepositoryIT {

    @Container
    static final PostgreSQLContainer<?> shardMaster0 =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("devices")
                    .withUsername("postgres")
                    .withPassword("postgres")
                    .withInitScript("db/migration/v1/V0__init-ds0.sql");

    @Container
    static final PostgreSQLContainer<?> shardReplica0 =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("devices")
                    .withUsername("postgres")
                    .withPassword("postgres")
                    .withInitScript("db/migration/v1/V0__init-ds0.sql");

    @Container
    static final PostgreSQLContainer<?> shardMaster1 =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("devices")
                    .withUsername("postgres")
                    .withPassword("postgres")
                    .withInitScript("db/migration/v1/V0__init-ds0.sql");

    @Container
    static final PostgreSQLContainer<?> shardReplica1 =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("devices")
                    .withUsername("postgres")
                    .withPassword("postgres")
                    .withInitScript("db/migration/v1/V0__init-ds0.sql");



    @Autowired
    private DataSource dataSource;
    @Autowired NamedParameterJdbcTemplate jdbc;
    @Autowired DeviceRepositoryCustom deviceRepository;
    @Autowired MeterRegistry meterRegistry;     // Micrometer
    @Autowired ShardMetrics shardMetrics;       // твой бин, инкрементящий counters


    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry r) {
        // Настраиваем ShardingSphere через Java-конфигурацию с переменными окружения
        r.add("sharding.datasource.shard-master-0.jdbc-url",
                () -> shardMaster0.getJdbcUrl() + "&currentSchema=device");
        r.add("sharding.datasource.shard-replica-0.jdbc-url",
                () -> shardReplica0.getJdbcUrl() + "&currentSchema=device");
        r.add("sharding.datasource.shard-master-1.jdbc-url",
                () -> shardMaster1.getJdbcUrl() + "&currentSchema=device");
        r.add("sharding.datasource.shard-replica-1.jdbc-url",
                () -> shardReplica1.getJdbcUrl() + "&currentSchema=device");
        r.add("sharding.datasource.username", () -> "postgres");
        r.add("sharding.datasource.password", () -> "postgres");

        // JPA настройки
        r.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        r.add("spring.jpa.show-sql", () -> "false");
    }

    private static DeviceEntity newDevice(String id, String type) {
        DeviceEntity d = new DeviceEntity();
        d.setDeviceId(id);
        d.setDeviceType(type);
        d.setCreatedAt(Instant.now().toEpochMilli()); // long
        d.setMeta("{}"); // String
        return d;
    }

    private static int shardIdx(String deviceId) {
        return Math.floorMod(deviceId.hashCode(), 2);
    }

    private long countByIdsOnMaster(Collection<String> ids) {
        try (HintManager hm = HintManager.getInstance()) {
            hm.setWriteRouteOnly();
            var sql = "SELECT COUNT(*) FROM device.devices WHERE device_id IN (:ids)";
            return jdbc.queryForObject(sql, Map.of("ids", ids), Long.class);
        }
    }

    private String getDeviceTypeOnMaster(String id) {
        try (HintManager hm = HintManager.getInstance()) {
            hm.setWriteRouteOnly();
            var sql = "SELECT device_type FROM device.devices WHERE device_id = :id";
            return jdbc.queryForObject(sql, Map.of("id", id), String.class);
        }
    }

    private double counter(String name, String... tags) {
        var c = meterRegistry.find(name).tags(tags).counter();
        return c == null ? 0.0 : c.count();
    }

    // --- tests ---

    @Test
    void contextStarts_and_DataSourceAcceptsConnections() throws Exception {
        assertThat(dataSource).isNotNull();
        try (var c = dataSource.getConnection();
             var st = c.createStatement();
             var rs = st.executeQuery("SELECT 1")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(1);
        }
    }

    @Test
    void upsertOne_respectsTypes_updatesDevice_and_incrementsMetrics() {
        String id = "dev-ONE-123";
        int shard = shardIdx(id);
        double before = counter(ShardMetrics.M_DEVICE_PROCESSED, "shard", String.valueOf(shard));

        // insert
        var d1 = newDevice(id, "SENSOR");
        int rows1 = deviceRepository.upsertOne(d1, shardMetrics);
        assertThat(rows1).isEqualTo(1);
        assertThat(countByIdsOnMaster(List.of(id))).isEqualTo(1);
        assertThat(getDeviceTypeOnMaster(id)).isEqualTo("SENSOR");

        // update
        var d2 = newDevice(id, "SENSOR_V2");
        int rows2 = deviceRepository.upsertOne(d2, shardMetrics);
        assertThat(rows2).isEqualTo(1);
        assertThat(getDeviceTypeOnMaster(id)).isEqualTo("SENSOR_V2");

        double after = counter(ShardMetrics.M_DEVICE_PROCESSED, "shard", String.valueOf(shard));
        assertThat(Math.round(after - before)).isEqualTo(2);
    }

    @Test
    void upsertBatch_insertsMany_acrossShards_and_incrementsMetricsPerShard() {
        var ids = List.of("dev-A-001", "dev-B-002", "dev-C-003", "dev-D-004");
        var batch = ids.stream()
                .map(id -> newDevice(id, "TYPE-" + id.substring(id.length()-3)))
                .collect(Collectors.toList());

        double before0 = counter(ShardMetrics.M_DEVICE_PROCESSED, "shard", "0");
        double before1 = counter(ShardMetrics.M_DEVICE_PROCESSED, "shard", "1");

        long affected = deviceRepository.upsertBatch(batch, shardMetrics);
        assertThat(affected).isEqualTo(ids.size());
        assertThat(countByIdsOnMaster(ids)).isEqualTo(ids.size());

        long s0 = ids.stream().filter(id -> shardIdx(id) == 0).count();
        long s1 = ids.stream().filter(id -> shardIdx(id) == 1).count();

        double after0 = counter(ShardMetrics.M_DEVICE_PROCESSED, "shard", "0");
        double after1 = counter(ShardMetrics.M_DEVICE_PROCESSED, "shard", "1");

        assertThat(Math.round(after0 - before0)).isEqualTo(s0);
        assertThat(Math.round(after1 - before1)).isEqualTo(s1);
    }


    @Test
    void upsertOne_updatesExistingDevice_fieldsAreChanged() {
        String id = "dev-UPDATE-001";
        int shard = shardIdx(id);

        double before = counter(ShardMetrics.M_DEVICE_PROCESSED, "shard", String.valueOf(shard));

        // insert
        var v1 = newDevice(id, "SENSOR_V1");
        v1.setCreatedAt(111_111L);
        v1.setMeta("{\"ver\":1}");
        assertThat(deviceRepository.upsertOne(v1, shardMetrics)).isEqualTo(1);
        assertThat(countByIdsOnMaster(List.of(id))).isEqualTo(1);
        var row1 = getRowOnMaster(id);
        assertThat(row1.deviceType()).isEqualTo("SENSOR_V1");
        assertThat(row1.createdAt()).isEqualTo(111_111L);
        assertThat(row1.meta()).isEqualTo("{\"ver\":1}");

        // update (upsert)
        var v2 = newDevice(id, "SENSOR_V2");
        v2.setCreatedAt(222_222L);
        v2.setMeta("{\"ver\":2}");
        assertThat(deviceRepository.upsertOne(v2, shardMetrics)).isEqualTo(1);

        var row2 = getRowOnMaster(id);
        assertThat(row2.deviceType()).isEqualTo("SENSOR_V2");
        assertThat(row2.createdAt()).isEqualTo(222_222L);
        assertThat(row2.meta()).isEqualTo("{\"ver\":2}");

        double after = counter(ShardMetrics.M_DEVICE_PROCESSED, "shard", String.valueOf(shard));
        assertThat(Math.round(after - before)).isEqualTo(2);
    }

    @Test
    void upsertBatch_updatesExistingAndInsertsNew_devices() {
        // подготовим: одна существующая запись
        String existing = "dev-BATCH-EXIST";
        var ex = newDevice(existing, "OLD");
        ex.setCreatedAt(10L);
        ex.setMeta("{\"m\":\"old\"}");
        assertThat(deviceRepository.upsertOne(ex, shardMetrics)).isEqualTo(1);

        // партия: существующий + два новых
        var ids = List.of(existing, "dev-B1", "dev-B2");
        var batch = new ArrayList<DeviceEntity>();

        var upd = newDevice(existing, "NEW");
        upd.setCreatedAt(20L);
        upd.setMeta("{\"m\":\"new\"}");
        batch.add(upd);

        var n1 = newDevice("dev-B1", "N1");
        n1.setCreatedAt(101L);
        n1.setMeta("{}");
        batch.add(n1);

        var n2 = newDevice("dev-B2", "N2");
        n2.setCreatedAt(102L);
        n2.setMeta("{\"x\":1}");
        batch.add(n2);

        long affected = deviceRepository.upsertBatch(batch, shardMetrics);
        assertThat(affected).isEqualTo(3);
        assertThat(countByIdsOnMaster(ids)).isEqualTo(3);

        // существующая запись обновилась
        var rowExisting = getRowOnMaster(existing);
        assertThat(rowExisting.deviceType()).isEqualTo("NEW");
        assertThat(rowExisting.createdAt()).isEqualTo(20L);
        assertThat(rowExisting.meta()).isEqualTo("{\"m\":\"new\"}");

        // новые появились
        var r1 = getRowOnMaster("dev-B1");
        assertThat(r1.deviceType()).isEqualTo("N1");
        assertThat(r1.createdAt()).isEqualTo(101L);
        assertThat(r1.meta()).isEqualTo("{}");

        var r2 = getRowOnMaster("dev-B2");
        assertThat(r2.deviceType()).isEqualTo("N2");
        assertThat(r2.createdAt()).isEqualTo(102L);
        assertThat(r2.meta()).isEqualTo("{\"x\":1}");
    }


    @Test
    void upsertOne_withNullId_throws_and_doesNotIncrementProcessedMetric() {
        var bad = newDevice(null, "BAD");
        bad.setCreatedAt(1L);
        bad.setMeta("{}");

        int shard = 0; // неважно какой — метрика не должна увеличиться ни по какому
        double before0 = counter(ShardMetrics.M_DEVICE_PROCESSED, "shard", "0");
        double before1 = counter(ShardMetrics.M_DEVICE_PROCESSED, "shard", "1");

        assertThatThrownBy(() -> deviceRepository.upsertOne(bad, shardMetrics))
                .isInstanceOf(Exception.class);

        double after0 = counter(ShardMetrics.M_DEVICE_PROCESSED, "shard", "0");
        double after1 = counter(ShardMetrics.M_DEVICE_PROCESSED, "shard", "1");
        assertThat(Math.round(after0 - before0)).isEqualTo(0);
        assertThat(Math.round(after1 - before1)).isEqualTo(0);
    }


    @Test
    void upsertBatch_withEmptyList_returnsZero_andDoesNothing() {
        double before0 = counter(ShardMetrics.M_DEVICE_PROCESSED, "shard", "0");
        double before1 = counter(ShardMetrics.M_DEVICE_PROCESSED, "shard", "1");

        long affected = deviceRepository.upsertBatch(List.of(), shardMetrics);
        assertThat(affected).isEqualTo(0);

        double after0 = counter(ShardMetrics.M_DEVICE_PROCESSED, "shard", "0");
        double after1 = counter(ShardMetrics.M_DEVICE_PROCESSED, "shard", "1");
        assertThat(Math.round(after0 - before0)).isEqualTo(0);
        assertThat(Math.round(after1 - before1)).isEqualTo(0);
    }


    @Test
    void upsertOne_invalidInput_nullId_incrementsInvalidInputError_andFails() {
        var bad = new DeviceEntity();
        bad.setDeviceId(null);
        bad.setDeviceType("X");
        bad.setCreatedAt(1L);
        bad.setMeta("{}");

        assertThatThrownBy(() -> deviceRepository.upsertOne(bad, shardMetrics))
                .isInstanceOfAny(InvalidDataAccessApiUsageException.class, DataIntegrityViolationException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)

                .hasMessageContaining("deviceId");

    }


    private record DeviceRow(String deviceType, long createdAt, String meta) {}

    private DeviceRow getRowOnMaster(String id) {
        try (HintManager hm = HintManager.getInstance()) {
            hm.setWriteRouteOnly();
            var sql = """
            SELECT device_type, created_at, meta
            FROM device.devices
            WHERE device_id = :id
        """;
            return jdbc.queryForObject(sql, Map.of("id", id), (rs, rn) ->
                    new DeviceRow(rs.getString("device_type"), rs.getLong("created_at"), rs.getString("meta")));
        }
    }

}
