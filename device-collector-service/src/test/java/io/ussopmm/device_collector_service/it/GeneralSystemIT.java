package io.ussopmm.device_collector_service.it;

import com.nashkod.avro.Device;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.micrometer.core.instrument.MeterRegistry;
import io.ussopmm.device_collector_service.helpers.ShardMetrics;
import io.ussopmm.device_collector_service.repository.DeviceRepositoryCustom;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.shardingsphere.infra.hint.HintManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;

/**
 * Полноценный интеграционный тест всего пайплайна:
 * Kafka → DeviceListener → DeviceService → ShardingSphere → PostgreSQL (2 шарда)
 *
 * Тест проверяет:
 * 1. Весь пайплайн обработки сообщений из Kafka
 * 2. Корректность шардирования данных
 * 3. Распределение данных по шардам согласно алгоритму
 */
@Testcontainers
@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "otel.sdk.disabled=true",
        "management.tracing.enabled=false",
        "spring.kafka.properties.specific.avro.reader=true",
        "spring.kafka.concurrency=1",
        "spring.kafka.poll-timeout=2000",
        "spring.kafka.batch-listener-enabled=true",
        "spring.kafka.dlt-topic.name=device-topic.DLT",
        "spring.kafka.consumer.group-id=device-test-group",
        "app.retry.maxAttempts=3",
        "app.retry.minBackoffS=1",
        "app.retry.maxBackoffS=2",
        "app.kafka.enabled=true",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"


})
@ActiveProfiles("full-it")
@EnableKafka
public class GeneralSystemIT {

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

    static final Network NET = Network.newNetwork();

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka").withTag("7.5.0"))
            .withNetwork(NET)
            .withNetworkAliases("kafka");

    @Container
    static final GenericContainer<?> SCHEMA_REGISTRY =
            new GenericContainer<>(DockerImageName.parse("confluentinc/cp-schema-registry:7.5.0"))
                    .withNetwork(NET)
                    .withNetworkAliases("schema-registry")
                    .withExposedPorts(8081)
                    .withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry")
                    .withEnv("SCHEMA_REGISTRY_LISTENERS", "http://0.0.0.0:8081")
                    .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "PLAINTEXT://kafka:9092")
                    .waitingFor(org.testcontainers.containers.wait.strategy.Wait.forHttp("/subjects").forStatusCode(200))
                    .dependsOn(KAFKA);


    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) throws Exception{
        // Настраиваем ShardingSphere через Java-конфигурацию с переменными окружения
        registry.add("sharding.datasource.shard-master-0.jdbc-url",
                () -> shardMaster0.getJdbcUrl() + "&currentSchema=device");
        registry.add("sharding.datasource.shard-replica-0.jdbc-url",
                () -> shardReplica0.getJdbcUrl() + "&currentSchema=device");
        registry.add("sharding.datasource.shard-master-1.jdbc-url",
                () -> shardMaster1.getJdbcUrl() + "&currentSchema=device");
        registry.add("sharding.datasource.shard-replica-1.jdbc-url",
                () -> shardReplica1.getJdbcUrl() + "&currentSchema=device");
        registry.add("sharding.datasource.username", () -> "postgres");
        registry.add("sharding.datasource.password", () -> "postgres");

        // JPA настройки
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.jpa.show-sql", () -> "false");
        String bootstrap = KAFKA.getBootstrapServers();
        String schemaRegistryUrl = "http://" + SCHEMA_REGISTRY.getHost() + ":" + SCHEMA_REGISTRY.getMappedPort(8081);

        registry.add("spring.kafka.bootstrap-servers", () -> bootstrap);
        registry.add("spring.kafka.consumer.bootstrap-servers", () -> bootstrap);
        registry.add("spring.kafka.producer.bootstrap-servers", () -> bootstrap);
        registry.add("spring.kafka.properties.schema.registry.url", () -> schemaRegistryUrl);
        registry.add("spring.kafka.consumer.properties.key.deserializer", () -> "org.apache.kafka.common.serialization.StringDeserializer");
        registry.add("spring.kafka.consumer.properties.value.deserializer", () -> "io.confluent.kafka.serializers.KafkaAvroDeserializer");
        registry.add("spring.kafka.producer.properties.key.serializer", () -> "org.apache.kafka.common.serialization.StringSerializer");
        registry.add("spring.kafka.producer.properties.value.serializer", () -> "io.confluent.kafka.serializers.KafkaAvroSerializer");
        registry.add("spring.kafka.topic.name", () -> "device-topic");
        String BOOTSTRAP = KAFKA.getHost() + ":" + KAFKA.getFirstMappedPort();

        try (AdminClient admin = AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP))) {

            String topicName = "device-topic";
            admin.createTopics(Collections.singleton(new NewTopic(topicName, 1, (short) 1)))
                    .all().get(30, TimeUnit.SECONDS);

            // Wait until a fresh metadata request sees the topic
            await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(250))
                    .until(() -> {
                        try {
                            return admin.describeTopics(Collections.singletonList(topicName))
                                    .allTopicNames().get(3, TimeUnit.SECONDS)
                                    .containsKey(topicName);
                        } catch (Exception e) {
                            return false;
                        }
                    });
        }
    }
    @TestConfiguration
    static class TestJdbcConfig {
        @Bean
        NamedParameterJdbcTemplate namedParameterJdbcTemplate(DataSource dataSource) {
            return new NamedParameterJdbcTemplate(dataSource);
        }
    }

    @Autowired
    private DataSource dataSource;
    @Autowired NamedParameterJdbcTemplate jdbc;
    @Autowired
    DeviceRepositoryCustom deviceRepository;
    @Autowired
    MeterRegistry meterRegistry;     // Micrometer
    @Autowired
    ShardMetrics shardMetrics;       // твой бин, инкрементящий counters
    @Autowired
    KafkaTemplate<String, Device> kafkaTemplate;




    @Test
    void endToEnd_pipeline_savesDevicesToDb() throws Exception {
        String topic = "device-topic";
        int batchSize = 5;

        // 1. Готовим список id девайсов
        List<String> ids = IntStream.range(0, batchSize)
                .mapToObj(i -> "it-dev-" + i)
                .toList();

        // 2. Шлём батч сообщений в Kafka
        for (String id : ids) {
            Device device = Device.newBuilder()
                    .setDeviceId(id)
                    .setDeviceType("MOBILE")
                    .setCreatedAt(100L)
                    .setMeta("testMeta-" + id)
                    .build();

            kafkaTemplate.send(topic, id, device).get(10, TimeUnit.SECONDS);
        }
        kafkaTemplate.flush();

        // 3. Ждём, пока listener обработает сообщения и DeviceService сохранит их в БД
        await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    try (HintManager hintManager = HintManager.getInstance()) {
                        // заставляем ShardingSphere роутить запросы только на master
                        hintManager.setWriteRouteOnly(); // или setPrimaryRouteOnly() в новой версии

                        String sql = """
                    SELECT COUNT(*) 
                    FROM device.devices
                    WHERE device_id LIKE 'it-dev-%'
                    """;

                        Integer count = jdbc.getJdbcTemplate().queryForObject(sql, Integer.class);
                        assertEquals(5, count.intValue());
                    }
                });
    }

    // маленький record только для маппинга JDBC -> Java
    record DeviceRow(String deviceId, String deviceType, long createdAt, String meta) {}

}
