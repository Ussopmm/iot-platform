package io.ussopmm.eventcollectorservice.util;

import com.nashkod.avro.DeviceEvent;
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.common.TopicPartition;
import org.junit.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
public class ApacheAvroSerializerTest {

    private final MockSchemaRegistryClient client =  new MockSchemaRegistryClient();

    @AfterEach
    void cleanUp() {
        client.reset();
    }


    @Test
    public void mockConsumer_avroDeserialization_roundTrip() {
        final String topic = "device-id-topic";
        final TopicPartition tp =  new TopicPartition(topic, 0);

        MockSchemaRegistryClient registry = new MockSchemaRegistryClient();
        Map<String, Object> serdeProps = new HashMap<>();
        serdeProps.put(KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, "mock://test");
        serdeProps.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true);

        KafkaAvroSerializer serializer = new KafkaAvroSerializer(registry);
        serializer.configure(serdeProps, false);

        KafkaAvroDeserializer deserializer =  new KafkaAvroDeserializer(registry);
        deserializer.configure(serdeProps, false);

        // Подготовим Avro-объект и сериализуем его в байты
        DeviceEvent original = DeviceEvent.newBuilder()
                .setDeviceId("dev-777")
                .setEventId("evt-999")
                .setTimestamp(1729799999000L)
                .setType("TEST")
                .setPayload("{\"test\":test}")
                .build();

        byte[] valueBytes = serializer.serialize(topic, original);

        MockConsumer<String, byte[]> consumer = new MockConsumer<>(OffsetResetStrategy.EARLIEST);

        consumer.subscribe(Collections.singleton(topic));
        consumer.rebalance(List.of(tp));
        consumer.updateBeginningOffsets(Map.of(tp, 0L));

        consumer.addRecord(new ConsumerRecord<>(topic, 0, 0L, "key-1", valueBytes));

        ConsumerRecords<String, byte[]> polled = consumer.poll(Duration.ofMillis(10));
        assertThat(polled.count()).isEqualTo(1);

        ConsumerRecord<String, byte[]> poll = polled.iterator().next();
        Object roundTrip = deserializer.deserialize(topic, poll.value());
        assertThat(roundTrip).isInstanceOf(DeviceEvent.class);

        DeviceEvent decoded = (DeviceEvent) roundTrip;

        assertThat(decoded).isEqualTo(original);
        assertThat(decoded.getDeviceId()).isEqualTo("dev-777");
        assertThat(decoded.getEventId()).isEqualTo("evt-999");
        assertThat(decoded.getType()).isEqualTo("TEST");
        assertThat(decoded.getPayload()).contains("test");


        serializer.close();
        deserializer.close();
        consumer.close();
    }
}
