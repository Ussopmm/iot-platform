//package io.ussopmm.device_collector_service.it;
//
//import io.micrometer.core.instrument.MeterRegistry;
//import io.ussopmm.device_collector_service.entity.DeviceEntity;
//import io.ussopmm.device_collector_service.helpers.ShardMetrics;
//import io.ussopmm.device_collector_service.repository.DeviceRepositoryCustom;
//import org.junit.jupiter.api.BeforeAll;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.io.TempDir;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
//import org.springframework.test.context.ActiveProfiles;
//import org.springframework.test.context.DynamicPropertyRegistry;
//import org.springframework.test.context.DynamicPropertySource;
//import org.testcontainers.containers.PostgreSQLContainer;
//import org.testcontainers.junit.jupiter.Container;
//import org.testcontainers.junit.jupiter.Testcontainers;
//
//import java.io.IOException;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.util.Map;
//
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.assertj.core.api.Assertions.assertThatThrownBy;
//
//@Testcontainers
//@ActiveProfiles("test")
//@SpringBootTest( properties = {
//        // статичные вещи
//        "server.port=0",
//        "spring.jpa.hibernate.ddl-auto=none",
//        "spring.jpa.show-sql=false",
//        "spring.flyway.enabled=false",
//        "spring.sql.init.mode=never",
//
//        // отрубаем Kafka/Streams/TaskExecution автоконфиги, чтобы контекст не тянул их зря
//        "spring.autoconfigure.exclude=" +
//                "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration," +
//                "org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration",
//
//        // если есть ваши флаги
//        "app.kafka.enabled=false",
//        "kafka.enabled=false",
//        "otel.sdk.disabled=true",
//        "management.tracing.enabled=false",
//        "spring.kafka.concurrency=1",
//        "spring.kafka.poll-timeout=2000",
//        "spring.kafka.batch-listener-enabled=true"
//})
//class DeviceRepositoryInvalidIT {
//
//    @Container
//    static final PostgreSQLContainer<?> shardMaster0 =
//            new PostgreSQLContainer<>("postgres:16-alpine")
//                    .withDatabaseName("devices")
//                    .withUsername("postgres")
//                    .withPassword("postgres")
//                    .withInitScript("db/migration/v1/V0__init-ds0.sql");
//
//    @TempDir
//    static Path tempDir;
//    static Path yaml;
//
//    @BeforeAll
//    static void generatedShardingYaml() throws IOException {
//        String m0 = shardMaster0.getJdbcUrl() + "&currentSchema=device";
//        // «мертвый» URL для мастера шарда 1:
//        String m1 = "jdbc:postgresql://127.0.0.1:65530/devices?currentSchema=device";
//
//        String content = ""
//                + "dataSources:\n"
//                + "  shard_master_0:\n"
//                + "    dataSourceClassName: com.zaxxer.hikari.HikariDataSource\n"
//                + "    jdbcUrl: " + m0 + "\n"
//                + "    username: postgres\n"
//                + "    password: postgres\n"
//                + "  shard_replica_0:\n"
//                + "    dataSourceClassName: com.zaxxer.hikari.HikariDataSource\n"
//                + "    jdbcUrl: " + m0 + "\n" // читаем тоже из живого мастера, чтобы не мешал
//                + "    username: postgres\n"
//                + "    password: postgres\n"
//                + "  shard_master_1:\n"
//                + "    dataSourceClassName: com.zaxxer.hikari.HikariDataSource\n"
//                + "    jdbcUrl: " + m1 + "\n"
//                + "    username: postgres\n"
//                + "    password: postgres\n"
//                + "  shard_replica_1:\n"
//                + "    dataSourceClassName: com.zaxxer.hikari.HikariDataSource\n"
//                + "    jdbcUrl: " + m1 + "\n"
//                + "    username: postgres\n"
//                + "    password: postgres\n"
//                + "rules:\n"
//                + "- !READWRITE_SPLITTING\n"
//                + "  dataSourceGroups:\n"
//                + "    shard0:\n"
//                + "      writeDataSourceName: shard_master_0\n"
//                + "      readDataSourceNames: [ shard_replica_0 ]\n"
//                + "      loadBalancerName: roundRobin\n"
//                + "    shard1:\n"
//                + "      writeDataSourceName: shard_master_1\n"
//                + "      readDataSourceNames: [ shard_replica_1 ]\n"
//                + "      loadBalancerName: roundRobin\n"
//                + "  loadBalancers:\n"
//                + "    roundRobin:\n"
//                + "      type: ROUND_ROBIN\n"
//                + "- !SHARDING\n"
//                + "  tables:\n"
//                + "    devices:\n"
//                + "      actualDataNodes: shard_master_${0..1}.devices\n"
//                + "      tableStrategy:\n"
//                + "        none:\n"
//                + "      databaseStrategy:\n"
//                + "        standard:\n"
//                + "          shardingColumn: device_id\n"
//                + "          shardingAlgorithmName: deviceid_hash_mod\n"
//                + "  shardingAlgorithms:\n"
//                + "    deviceid_hash_mod:\n"
//                + "      type: INLINE\n"
//                + "      props:\n"
//                + "        algorithm-expression: shard_master_${Math.abs(device_id.hashCode()) % 2}\n";
//
//        yaml = tempDir.resolve("sharding-it-outage.yml");
//        Files.writeString(yaml, content);
//    }
//
//    @DynamicPropertySource
//    static void overrideDatasource(DynamicPropertyRegistry registry) {
//        registry.add("spring.datasource.url",
//                () -> "jdbc:shardingsphere:absolutepath:" + yaml.toAbsolutePath());
//    }
//
//    @Autowired
//    DeviceRepositoryCustom repo;
//    @Autowired
//    ShardMetrics shardMetrics;
//    @Autowired
//    MeterRegistry meterRegistry;
//    @Autowired
//    NamedParameterJdbcTemplate jdbc;
//
//
//    private static int shardIdx(String id) { return Math.floorMod(id.hashCode(), 2); }
//    private double err(String shard, String cause) {
//        var c = meterRegistry.find(ShardMetrics.M_DEVICE_ERRORS)
//                .tags("shard", shard, "cause", cause).counter();
//        return c == null ? 0.0 : c.count();
//    }
//
//    private long countOnMaster(String id) {
//        try (var hm = org.apache.shardingsphere.infra.hint.HintManager.getInstance()) {
//            hm.setWriteRouteOnly();
//            return jdbc.queryForObject(
//                    "SELECT COUNT(*) FROM device.devices WHERE device_id=:id",
//                    Map.of("id", id), Long.class);
//        }
//    }
//
//    @Test
//    void upsertOne_fails_whenTargetShardIsDown_andIncrementsErrorMetric() {
//        // подберём id, который точно попадёт в shard=1 (down)
//        String id = "must-go-to-shard-1";
//        while (shardIdx(id) != 1) id = id + "x";
//
//        var d = new DeviceEntity();
//        d.setDeviceId(id);
//        d.setDeviceType("ANY");
//        d.setCreatedAt(1L);
//        d.setMeta("{}");
//
//        assertThatThrownBy(() -> repo.upsertOne(d, shardMetrics))
//                .isInstanceOf(Exception.class);
//
//        assertThat(countOnMaster(id)).isEqualTo(0);
//    }
//
//}
