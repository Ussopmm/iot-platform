package io.ussopmm.eventcollectorservice.producer;

import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class EventProducerTest {

    @Test
    public void sendEvent_ShouldSendEventToKafkaTopic() {
        //given
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        EventProducer producer = new EventProducer(kafkaTemplate);
        ReflectionTestUtils.setField(producer, "topic", "device-id-topic");
        String deviceId = "device123";

        SendResult<String, String> sendResult = mock(SendResult.class);
        RecordMetadata recordMetadata = mock(RecordMetadata.class);
        when(sendResult.getRecordMetadata()).thenReturn(recordMetadata);
        when(kafkaTemplate.send("device-id-topic", deviceId))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        //when
        CompletableFuture<RecordMetadata> result = producer.sendEvent(deviceId);
        //then
        assertThat(result.join()).isEqualTo(recordMetadata);
        verify(kafkaTemplate).send("device-id-topic", deviceId);
        verifyNoMoreInteractions(kafkaTemplate);
    }

    @Test
    public void sendEvent_ShouldThrowExceptionWhenKafkaFails() {
        //given
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        EventProducer producer = new EventProducer(kafkaTemplate);
        ReflectionTestUtils.setField(producer, "topic", "device-id-topic");
        String deviceId = "device123";

        CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka Error"));

        when(kafkaTemplate.send("device-id-topic", deviceId))
                .thenReturn(failedFuture);
        //when
        CompletableFuture<RecordMetadata> result = producer.sendEvent(deviceId);
        //then
        assertThatThrownBy(() -> result.join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(RuntimeException.class)
                .hasMessageContaining("Kafka Error");
        verify(kafkaTemplate).send("device-id-topic", deviceId);
        verifyNoMoreInteractions(kafkaTemplate);
    }


}
