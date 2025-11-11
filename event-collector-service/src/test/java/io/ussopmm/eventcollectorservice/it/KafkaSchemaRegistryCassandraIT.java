package io.ussopmm.eventcollectorservice.it;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Row;
import com.nashkod.avro.Device;
import com.nashkod.avro.DeviceEvent;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.CassandraContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest
public class KafkaSchemaRegistryCassandraIT {

    static final Network NET = Network.newNetwork();

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka").withTag("7.5.0"))
            .withNetwork(NET)
            .withNetworkAliases("kafka")      // internal DNS name
            .withReuse(false);

    @Container
    static final GenericContainer<?> SCHEMA_REGISTRY =
            new GenericContainer<>(DockerImageName.parse("confluentinc/cp-schema-registry").withTag("7.5.0"))
                    .withNetwork(NET)
                    .withNetworkAliases("schema-registry")
                    .withExposedPorts(8081)
                    .withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry")
                    .withEnv("SCHEMA_REGISTRY_LISTENERS", "http://0.0.0.0:8081")
                    .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "PLAINTEXT://kafka:9092")
                    .dependsOn(KAFKA);

    @Container
    static final CassandraContainer<?> CASSANDRA =
            new CassandraContainer<>(DockerImageName.parse("cassandra").withTag("latest"))
                    .withNetwork(NET)
                    .withNetworkAliases("cassandra")
                    .withInitScript("cassandra/init.cql");;

    static {
        Startables.deepStart(Stream.of(KAFKA, SCHEMA_REGISTRY, CASSANDRA)).join();
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry r) {

        // If you use Avro with SpecificRecord:
        r.add("spring.kafka.properties.specific.avro.reader", () -> "true");

        // Cassandra (Datastax driver)
        r.add("CASSANDRA_HOST", CASSANDRA::getHost);
        r.add("CASSANDRA_PORT", () -> String.valueOf(CASSANDRA.getFirstMappedPort()));
        r.add("spring.cassandra.contact-points", () ->
                CASSANDRA.getHost() + ":" + CASSANDRA.getFirstMappedPort());
        r.add("spring.cassandra.local-datacenter", () -> "datacenter1");
        r.add("spring.cassandra.keyspace-name", () -> "iot_platform");
    }

    @Autowired
    KafkaTemplate<String, DeviceEvent> kafkaTemplate;

    @Autowired
    CqlSession cqlSession;

    @BeforeAll
    static void initSchema() throws Exception {
        // Kafka
        System.setProperty("spring.kafka.bootstrap-servers", KAFKA.getBootstrapServers());
        System.setProperty("spring.kafka.consumer.bootstrap-servers", KAFKA.getBootstrapServers());
        System.setProperty("spring.kafka.producer.bootstrap-servers", KAFKA.getBootstrapServers());
        System.setProperty("spring.kafka.properties.schema.registry.url",
                "http://" + SCHEMA_REGISTRY.getHost() + ":" + SCHEMA_REGISTRY.getMappedPort(8081));

        // Cassandra - создаём keyspace и таблицу через CQL
        try (CqlSession session = CqlSession.builder()
                .addContactPoint(new InetSocketAddress(CASSANDRA.getHost(), CASSANDRA.getFirstMappedPort()))
                .withLocalDatacenter("datacenter1")
                .build()) {

            session.execute("CREATE KEYSPACE IF NOT EXISTS iot_platform WITH replication = {'class':'SimpleStrategy','replication_factor':1};");
            session.execute("""
            CREATE TABLE IF NOT EXISTS iot_platform.device_events_by_device(
              device_id text,
              event_id text,
              timestamp bigint,
              type text,
              payload text,
              PRIMARY KEY (device_id, timestamp, event_id)
            ) WITH CLUSTERING ORDER BY (timestamp DESC)
        """);
        }

        // Topic creation using the SAME host-mapped bootstrap as Spring
        String BOOTSTRAP = KAFKA.getHost() + ":" + KAFKA.getFirstMappedPort();

        try (AdminClient admin = AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP))) {

            String topicName = "events-topic";
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



    @Test
    public void kafka_cassandra_test_shouldSendEventToKafkaTopicConsumeFromKafkaAndStoreInCassandra() {
        // Create an object
        var device = new Device("dev-1", "testType", 1L, "testMetadata");

        DeviceEvent deviceEvent = DeviceEvent.newBuilder()
                .setDevice(device)
                .setEventId("e-999")
                .setTimestamp(System.currentTimeMillis())
                .setType("Test")
                .setPayload("test")
                .build();
        // Send to kafka
        kafkaTemplate.send("events-topic", deviceEvent);

        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(250)).untilAsserted(() -> {
            Row row = cqlSession.execute(
                    "SELECT event_id, type FROM iot_platform.device_events_by_device WHERE device_id='d-123' LIMIT 1"
            ).one();
            assertNotNull(row);
            assertEquals("e-999", row.getString("event_id"));
            assertEquals("Test", row.getString("type"));
        });
    }




}
