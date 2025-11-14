package io.ussopmm.device_collector_service.it;

import com.nashkod.avro.Device;
import io.ussopmm.device_collector_service.helpers.ShardMetrics;
import io.ussopmm.device_collector_service.service.DeviceService;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@Testcontainers
@ActiveProfiles("kafka-it")
@SpringBootTest(
        properties = {
//                "spring.kafka.topic.name=device-topic-test",
//                "spring.kafka.dlt-topic.name=device-topic-test.DLT",
                "spring.kafka.consumer.group-id=device-test-group",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
                        "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
                        "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration," +
                        "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration",
                "spring.flyway.enabled=false",
                "spring.jpa.hibernate.ddl-auto=none",
                "app.retry.maxAttempts=3",
                "app.retry.minBackoffS=1",
                "app.retry.maxBackoffS=2",
                "app.kafka.enabled=true",
                "management.tracing.enabled=false",
                "otel.sdk.disabled=true",
                "spring.kafka.properties.specific.avro.reader=true",
                "spring.kafka.concurrency=1",
                "spring.kafka.poll-timeout=2000",
                "spring.kafka.batch-listener-enabled=true",
                "spring.kafka.dlt-topic.name=device-topic.DLT"
        }
)
public class DeviceListenerIT {

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
    static void kafkaProperties(DynamicPropertyRegistry registry) throws Exception {
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

    @MockitoBean
    NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @MockitoBean(name = "deviceService")
    DeviceService deviceService;

    @Autowired
    KafkaTemplate<String, Device> kafkaTemplate;

    @Autowired
    ShardMetrics metrics;

    @BeforeEach
    void setUp() {
        reset(deviceService);
        // любой вызов save -> успешно, возвращаем количество сохранённых девайсов
        when(deviceService.save(anyList(), any(ShardMetrics.class)))
                .thenAnswer(invocation -> {
                    var list = (java.util.List<?>) invocation.getArgument(0);
                    return Mono.just(list.size());
                });
    }


    @Test
    void listenerConsumesMessage_andCallsDeviceService() throws Exception {
        // 1. Готовим Avro Device (подставь реальные поля/билдер)
        Device device = Device.newBuilder()
                .setDeviceId("dev-1")
                .setDeviceType("MOBILE")
                .setCreatedAt(100L)
                .setMeta("testMeta")
                // остальные обязательные поля, если есть
                .build();

        String topic = "device-topic"; // то же самое, что ты создаёшь в DynamicPropertySource

        // 2. Отправляем сообщение в Kafka
        kafkaTemplate.send(topic, "key-1", device).get(10, TimeUnit.SECONDS);
        kafkaTemplate.flush();

        // 3. Ждём, пока listener отработает и дернёт deviceService.save(...)
        await()
                .atMost(15, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    verify(deviceService).save(
                            argThat((List<org.apache.kafka.clients.consumer.ConsumerRecord<String, Device>> records) ->
                                    records.size() == 1
                                            && "dev-1".equals(records.get(0).value().getDeviceId())
                                            && "MOBILE".equals(records.get(0).value().getDeviceType())
                            ),
                            org.mockito.ArgumentMatchers.eq(metrics)
                    );
                });
    }



    @Test
    void listenerProcessesBatchOfDevices_happyPath() throws Exception {
        String topic = "device-topic";
        int batchSize = 5;

        // ожидаемые ID
        List<String> ids = IntStream.range(0, batchSize)
                .mapToObj(i -> "dev-" + i)
                .toList();
        Set<String> expectedIds = new HashSet<>(ids);

        // отправляем события
        for (String id : ids) {
            Device device = Device.newBuilder()
                    .setDeviceId(id)
                    .setDeviceType("MOBILE")
                    .setCreatedAt(100L)
                    .setMeta("testMeta" + id)
                    .build();

            kafkaTemplate.send(topic, id, device).get(10, TimeUnit.SECONDS);
        }
        kafkaTemplate.flush();

        await()
                .atMost(20, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    @SuppressWarnings("unchecked")
                    ArgumentCaptor<List<ConsumerRecord<String, Device>>> captor =
                            (ArgumentCaptor) ArgumentCaptor.forClass(List.class);

                    // важно: atLeastOnce, т.к. батчей может быть несколько
                    verify(deviceService, atLeastOnce()).save(captor.capture(), org.mockito.ArgumentMatchers.eq(metrics));

                    // собираем все записи из всех вызовов save(...)
                    Set<String> allIds = captor.getAllValues().stream()
                            .flatMap(list -> list.stream())
                            .map(rec -> rec.value().getDeviceId())
                            .collect(java.util.stream.Collectors.toSet());

                    // как только все 5 дошли — ассерт проходит, awaitility прекращает ждать
                    assertEquals(expectedIds, allIds);
                });
    }

}
