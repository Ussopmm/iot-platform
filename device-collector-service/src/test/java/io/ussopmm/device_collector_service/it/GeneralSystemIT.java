//package io.ussopmm.device_collector_service.it;
//
//import com.nashkod.avro.Device;
//import io.ussopmm.device_collector_service.helpers.ShardMetrics;
//import org.apache.shardingsphere.infra.hint.HintManager;
//import org.junit.jupiter.api.BeforeAll;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.io.TempDir;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.boot.test.context.TestConfiguration;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Primary;
//import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
//import org.springframework.kafka.annotation.EnableKafka;
//import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
//import org.springframework.kafka.core.DefaultKafkaProducerFactory;
//import org.springframework.kafka.core.KafkaTemplate;
//import org.springframework.test.context.ActiveProfiles;
//import org.springframework.test.context.DynamicPropertyRegistry;
//import org.springframework.test.context.DynamicPropertySource;
//import org.testcontainers.containers.GenericContainer;
//import org.testcontainers.containers.KafkaContainer;
//import org.testcontainers.containers.Network;
//import org.testcontainers.containers.PostgreSQLContainer;
//import org.testcontainers.junit.jupiter.Container;
//import org.testcontainers.junit.jupiter.Testcontainers;
//import org.testcontainers.shaded.org.awaitility.Awaitility;
//import org.testcontainers.utility.DockerImageName;
//
//import javax.sql.DataSource;
//import java.io.IOException;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.time.Duration;
//import java.util.*;
//
//import static org.assertj.core.api.Assertions.assertThat;
//
//@Testcontainers
//@SpringBootTest( properties = {
//        "server.port=0",
//        "spring.jpa.hibernate.ddl-auto=none",
//        "spring.jpa.show-sql=false",
//        "spring.flyway.enabled=false",
//        "spring.sql.init.mode=never",
//        "otel.sdk.disabled=true",
//        "management.tracing.enabled=false",
//        "spring.kafka.concurrency=1",
//        "spring.kafka.poll-timeout=2000",
//        "spring.kafka.batch-listener-enabled=true",
//        "app.kafka.enabled=true",
//        "spring.kafka.topic.name=device-topic-it",
//        "spring.kafka.consumer.group-id=device-collector-group-it",
//        "app.retry.maxAttempts=1",
//        "app.retry.minBackoffS=0",
//        "app.retry.maxBackoffS=1",
//        "spring.kafka.consumer.auto-offset-reset=earliest"
//})
//@ActiveProfiles("test")
//@EnableKafka
//public class GeneralSystemIT {
//
//    @Container
//    static final PostgreSQLContainer<?> shardMaster0 =
//            new PostgreSQLContainer<>("postgres:16-alpine")
//                    .withDatabaseName("devices")
//                    .withUsername("postgres")
//                    .withPassword("postgres")
//                    .withInitScript("db/migration/v1/V0__init-ds0.sql");
//
//    @Container
//    static final PostgreSQLContainer<?> shardReplica0 =
//            new PostgreSQLContainer<>("postgres:16-alpine")
//                    .withDatabaseName("devices")
//                    .withUsername("postgres")
//                    .withPassword("postgres")
//                    .withInitScript("db/migration/v1/V0__init-ds0.sql");
//
//    @Container
//    static final PostgreSQLContainer<?> shardMaster1 =
//            new PostgreSQLContainer<>("postgres:16-alpine")
//                    .withDatabaseName("devices")
//                    .withUsername("postgres")
//                    .withPassword("postgres")
//                    .withInitScript("db/migration/v1/V0__init-ds0.sql");
//
//    @Container
//    static final PostgreSQLContainer<?> shardReplica1 =
//            new PostgreSQLContainer<>("postgres:16-alpine")
//                    .withDatabaseName("devices")
//                    .withUsername("postgres")
//                    .withPassword("postgres")
//                    .withInitScript("db/migration/v1/V0__init-ds0.sql");
//
//
//    private static Path generatedShardingYaml;
//
//    @TempDir
//    static Path tempDir;
//
//    @BeforeAll
//    static void generateShardingYaml() throws IOException {
//        String m0 = shardMaster0.getJdbcUrl() + "&currentSchema=device";
//        String r0 = shardReplica0.getJdbcUrl() + "&currentSchema=device";
//        String m1 = shardMaster1.getJdbcUrl() + "&currentSchema=device";
//        String r1 = shardReplica1.getJdbcUrl() + "&currentSchema=device";
//
//        // YAML строго по вашей структуре, подставляем динамические jdbcUrl
//        String yaml = ""
//                + "dataSources:\n"
//                + "  shard_master_0:\n"
//                + "    dataSourceClassName: com.zaxxer.hikari.HikariDataSource\n"
//                + "    jdbcUrl: " + m0 + "\n"
//                + "    username: postgres\n"
//                + "    password: postgres\n"
//                + "  shard_replica_0:\n"
//                + "    dataSourceClassName: com.zaxxer.hikari.HikariDataSource\n"
//                + "    jdbcUrl: " + r0 + "\n"
//                + "    username: postgres\n"
//                + "    password: postgres\n"
//                + "  shard_master_1:\n"
//                + "    dataSourceClassName: com.zaxxer.hikari.HikariDataSource\n"
//                + "    jdbcUrl: " + m1 + "\n"
//                + "    username: postgres\n"
//                + "    password: postgres\n"
//                + "  shard_replica_1:\n"
//                + "    dataSourceClassName: com.zaxxer.hikari.HikariDataSource\n"
//                + "    jdbcUrl: " + r1 + "\n"
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
//                + "\n"
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
//        generatedShardingYaml = tempDir.resolve("sharding-it.yml");
//        Files.writeString(generatedShardingYaml, yaml);
//    }
//
//    static final Network NET = Network.newNetwork();
//
//    @Container
//    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka").withTag("7.5.0"))
//            .withNetwork(NET)
//            .withNetworkAliases("kafka");
//
//    @Container
//    static final GenericContainer<?> SCHEMA_REGISTRY =
//            new GenericContainer<>(DockerImageName.parse("confluentinc/cp-schema-registry:7.5.0"))
//                    .withNetwork(NET)
//                    .withNetworkAliases("schema-registry")
//                    .withExposedPorts(8081)
//                    .withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry")
//                    .withEnv("SCHEMA_REGISTRY_LISTENERS", "http://0.0.0.0:8081")
//                    .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "PLAINTEXT://kafka:9092")
//                    .waitingFor(org.testcontainers.containers.wait.strategy.Wait.forHttp("/subjects").forStatusCode(200))
//                    .dependsOn(KAFKA);
//
//    static final String SOURCE_TOPIC = "device-topic-it";
//    static final String DLT_TOPIC    = "device-topic-it.DLT";
//
//
//    @DynamicPropertySource
//    static void props(DynamicPropertyRegistry r) {
//        r.add("spring.datasource.driver-class-name", () -> "org.apache.shardingsphere.driver.ShardingSphereDriver");
//        r.add("spring.datasource.url", () -> "jdbc:shardingsphere:absolutepath:" + generatedShardingYaml.toAbsolutePath());
//
//        r.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
//        r.add("spring.kafka.properties.schema.registry.url",
//                () -> "http://" + SCHEMA_REGISTRY.getHost() + ":" + SCHEMA_REGISTRY.getMappedPort(8081));
//
//        // Консьюмер у приложения (чтобы @KafkaListener стартовал как в проде)
//        r.add("spring.kafka.consumer.properties.specific.avro.reader", () -> "true");
//        r.add("spring.kafka.consumer.value-deserializer",
//                () -> "io.confluent.kafka.serializers.KafkaAvroDeserializer");
//        r.add("spring.kafka.producer.value-serializer",
//                () -> "io.confluent.kafka.serializers.KafkaAvroSerializer");
//
//        // Топики (если код читает te же ключи)
//        r.add("spring.kafka.topic.name", () -> SOURCE_TOPIC);
//    }
//
//    @TestConfiguration
//    static class AvroTemplateCfg {
//        @Bean
//        @Primary
//        KafkaTemplate<String, Device> kafkaTemplate() {
//            Map<String, Object> cfg = new HashMap<>();
//            cfg.put("bootstrap.servers", KAFKA.getBootstrapServers());
//            cfg.put("key.serializer", org.apache.kafka.common.serialization.StringSerializer.class);
//            cfg.put("value.serializer", io.confluent.kafka.serializers.KafkaAvroSerializer.class);
//            cfg.put("schema.registry.url", "http://" + SCHEMA_REGISTRY.getHost() + ":" + SCHEMA_REGISTRY.getMappedPort(8081));
//            cfg.put("auto.register.schemas", true);
//            return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(cfg));
//        }
//    }
//
//    @Autowired
//    KafkaTemplate<String, Device> producer;
//    @Autowired
//    NamedParameterJdbcTemplate jdbc;
//    @Autowired
//    DataSource dataSource;
//    @Autowired
//    ShardMetrics metrics;
//
//    private static Device newDevice(String id, String type, long created, String meta) {
//        var d = new Device();
//        d.setDeviceId(id);
//        d.setDeviceType(type);
//        d.setCreatedAt(created);
//        d.setMeta(meta);
//        return d;
//    }
//
//    private long countOnMaster(Collection<String> ids) {
//        try (HintManager hm = HintManager.getInstance()) {
//            hm.setWriteRouteOnly();
//            return jdbc.queryForObject(
//                    "SELECT COUNT(*) FROM device.devices WHERE device_id IN (:ids)",
//                    Map.of("ids", ids), Long.class);
//        }
//    }
//
//    private String deviceTypeOnMaster(String id) {
//        try (HintManager hm = HintManager.getInstance()) {
//            hm.setWriteRouteOnly();
//            return jdbc.queryForObject(
//                    "SELECT device_type FROM device.devices WHERE device_id=:id",
//                    Map.of("id", id), String.class);
//        }
//    }
//
//
//    @Test
//    void fullPipeline_fromKafka_toShardedDB_success() {
//        // 1) шлём батч в исходный топик (как будто прод-продюсер)
//        var ids = List.of("dev-A-001", "dev-B-002", "dev-C-003", "dev-D-004");
//        ids.forEach(id -> producer.send(SOURCE_TOPIC, id, newDevice(id, "TYPE-"+id.substring(id.length()-3), 111L, "{}")));
//        producer.flush();
//
//        Awaitility.await().atMost(Duration.ofSeconds(10))
//                .until(() -> true); // или проверка какого-нибудь твоего health/флага
//
//        // 2) ждём, пока @KafkaListener обработает и deviceService.save(...) запишет в БД
//        Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
//            assertThat(countOnMaster(ids)).isEqualTo(ids.size());
//        });
//
//        // 3) точечно проверим одно обновление: пошлём апдейт существующего id
//        String sameId = ids.get(0);
//        producer.send(SOURCE_TOPIC, sameId, newDevice(sameId, "TYPE-UPDATED", 222L, "{\"v\":2}"));
//        producer.flush();
//
//        Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
//            assertThat(deviceTypeOnMaster(sameId)).isEqualTo("TYPE-UPDATED");
//        });
//
//        // 4) sanity: подключение через ShardingSphere работает
//        assertThat(dataSource).isNotNull();
//    }
//}
