package io.ussopmm.device_collector_service.it;

import com.fasterxml.jackson.databind.JsonSerializer;
import com.nashkod.avro.Device;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.ussopmm.device_collector_service.helpers.ShardMetrics;
import io.ussopmm.device_collector_service.listener.DeviceListener;
import io.ussopmm.device_collector_service.service.DeviceService;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@Testcontainers
@ActiveProfiles("kafka-it")
@SpringBootTest(properties = {
        "spring.kafka.topic.name=device-topic-it",
        "spring.kafka.dlt-topic.name=device-topic-it.DLT",
        "app.kafka.dlt-topic=device-topic-it.DLT",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
                "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
                "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
        "spring.flyway.enabled=false",
        "app.retry.maxAttempts=1",
        "app.retry.minBackoffS=1",
        "app.retry.maxBackoffS=2",
        "spring.kafka.concurrency=1",
        "spring.kafka.poll-timeout=2000",
        "spring.kafka.batch-listener-enabled=true",
        "management.tracing.enabled=false",
        "otel.sdk.disabled=true",
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.admin.auto-create=false"
})
public class DeviceListenerDltIT {
    static final String SOURCE_TOPIC = "device-topic-it";
    static final String DLT_TOPIC    = "device-topic-it.DLT";
    static final String KAFKA_URL = "kafka-test:9092";
    static final String SCHEMA_REGISTRY_URL = "http://schema-registry-test:8082";


    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.kafka.bootstrap-servers", () -> KAFKA_URL);
        r.add("spring.kafka.consumer.bootstrap-servers", () -> KAFKA_URL);
        r.add("spring.kafka.producer.bootstrap-servers", () -> KAFKA_URL);
        String registry = SCHEMA_REGISTRY_URL;
        r.add("spring.kafka.properties.schema.registry.url", () -> registry);
        r.add("spring.kafka.consumer.properties.key.deserializer", () -> "org.apache.kafka.common.serialization.StringDeserializer");
        r.add("spring.kafka.consumer.properties.value.deserializer", () -> "io.confluent.kafka.serializers.KafkaAvroDeserializer");
        r.add("spring.kafka.producer.properties.key.serializer", () -> "org.apache.kafka.common.serialization.StringSerializer");
        r.add("spring.kafka.producer.properties.value.serializer", () -> "io.confluent.kafka.serializers.KafkaAvroSerializer");
        r.add("app.kafka.dlt-topic", () -> DLT_TOPIC);
        r.add("app.kafka.source-topic", () -> SOURCE_TOPIC);
    }

    @TestConfiguration
    static class AvroTemplateConfig {
        @Bean("kafkaTemplate")
        @Primary
        KafkaTemplate<String, Device> kafkaTemplate() {
            Map<String, Object> cfg = new HashMap<>();
            cfg.put("bootstrap.servers", KAFKA_URL);
            cfg.put("key.serializer", org.apache.kafka.common.serialization.StringSerializer.class);
            cfg.put("value.serializer", KafkaAvroSerializer.class);
            String registry = SCHEMA_REGISTRY_URL;
            cfg.put("schema.registry.url", registry);
            return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(cfg));
        }
    }

    @Autowired
    DeviceListener listener;

    @MockitoBean
    NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @MockitoBean(name = "deviceService")
    private DeviceService deviceService;

    // ack замокаем, чтобы проверить, что вызвался после DLT
    Acknowledgment ack;

    @MockitoSpyBean(name = "kafkaTemplate")
    KafkaTemplate<String, Device> kafkaTemplate;

    @Autowired
    ShardMetrics metrics; // не обязателен, но пусть инициализируется

    @BeforeAll
    static void logBootstrap() throws Exception {
//        try (AdminClient admin = AdminClient.create(Map.of("bootstrap.servers", "kafka-test:9092"))) {
//            admin.deleteTopics(List.of(SOURCE_TOPIC, DLT_TOPIC)).all().get(10, TimeUnit.SECONDS);
//        }
    }

    @BeforeAll
    static void createTopics() throws Exception {
        try (AdminClient admin = AdminClient.create(Map.of("bootstrap.servers", KAFKA_URL))) {

            // Получаем список существующих топиков
            Set<String> existingTopics = admin.listTopics().names().get(30, TimeUnit.SECONDS);

            List<NewTopic> topicsToCreate = new ArrayList<>();

            if (!existingTopics.contains(SOURCE_TOPIC)) {
                topicsToCreate.add(new NewTopic(SOURCE_TOPIC, 1, (short) 1));
                System.out.println("Topic " + SOURCE_TOPIC + " will be created");
            } else {
                System.out.println("Topic " + SOURCE_TOPIC + " already exists");
            }

            if (!existingTopics.contains(DLT_TOPIC)) {
                topicsToCreate.add(new NewTopic(DLT_TOPIC, 1, (short) 1));
                System.out.println("Topic " + DLT_TOPIC + " will be created");
            } else {
                System.out.println("Topic " + DLT_TOPIC + " already exists");
            }

            // Создаем только отсутствующие топики
            if (!topicsToCreate.isEmpty()) {
                admin.createTopics(topicsToCreate).all().get(30, TimeUnit.SECONDS);
                System.out.println("Created " + topicsToCreate.size() + " topics");
            } else {
                System.out.println("All topics already exist, skipping creation");
            }
        }
    }

    @BeforeEach
    void setUp() {
        ack = mock(org.springframework.kafka.support.Acknowledgment.class);
        // фреймворк должен видеть, что save(...) всегда падает → ретраи → DLT
        when(deviceService.save(anyList(), any(ShardMetrics.class)))
                .thenReturn(Mono.error(new RuntimeException("boom")));
    }


    @Test
    void whenRetriesExhausted_messageIsPublishedToDLT_andAcked() {
        // Собираем входной ConsumerRecord так же, как его видит твой listener
        Device dev = new Device();          // SpecificRecord
        dev.setDeviceId("dlt-it-42");
        dev.setDeviceType("TEST");
        dev.setCreatedAt(1L);
        dev.setMeta("test meta");

        var rec = new org.apache.kafka.clients.consumer.ConsumerRecord<String, Device>(
                SOURCE_TOPIC, 0, 0L, "key-42", dev);
        rec.headers().add("source", "it".getBytes());

        listener.listen(List.of(rec), ack);
        var cap = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate, timeout(30000)).send(cap.capture());
        assertThat(((ProducerRecord<?, ?>) cap.getValue()).topic()).isEqualTo("device-topic-it.DLT");

        // Читаем из DLT авро-консюмером
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_URL); // используем внешний адрес
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "dlt-it-consumer");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put("schema.registry.url", SCHEMA_REGISTRY_URL);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, io.confluent.kafka.serializers.KafkaAvroDeserializer.class);
        props.put("specific.avro.reader", true);

        String gotKey = null;
        Device gotVal = null;

        try (var consumer = new KafkaConsumer<String, Device>(props)) {
            consumer.subscribe(List.of(DLT_TOPIC));

            long deadline = System.currentTimeMillis() + 60_000; // с запасом из-за backoff
            poll:
            while (System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, Device> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, Device> r : records) {
                    gotKey = r.key();
                    gotVal = r.value();
                    break poll;
                }
            }
        }

        assertThat(gotKey).isEqualTo("key-42");
        assertThat(gotVal).isNotNull();
        assertThat(gotVal.getDeviceId()).isEqualTo("dlt-it-42");
        assertThat(gotVal.getDeviceType()).isEqualTo("TEST");

        // ack должен быть вызван ПОСЛЕ publish в DLT (проверяем факт вызова)
        verify(ack, timeout(5000)).acknowledge();
    }

}
