package io.ussopmm.eventcollectorservice.producer;

import org.junit.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
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

        when(kafkaTemplate.send("device-id-topic", deviceId))
                .thenReturn(CompletableFuture.completedFuture(null));
        //when
        producer.sendEvent(deviceId);
        //then
        verify(kafkaTemplate).send("device-id-topic", deviceId);
        verifyNoMoreInteractions(kafkaTemplate);

    }

//    @Test
//    public void sendEvent_ShouldNotSendEventToKafkaTopicAndThrowException() {
//        String topic = "device-id-topic";
//        String deviceId = "dev-1";
//        RuntimeException root = new RuntimeException("kafka down");
//
//        // inject the @Value field for the unit test (since no Spring context is running)
//        ReflectionTestUtils.setField(producer, "topic", topic);
//
//        // Return a failed future so SUT goes into .exceptionally(...)
//        when(kafkaTemplate.send(eq(topic), eq(deviceId)))
//                .thenReturn(CompletableFuture.failedFuture(root));
//
//        // Act + Assert
//        CompletionException thrown =
//                assertThrows(CompletionException.class,
//                        () -> producer.sendEvent(deviceId).join());
//
//        assertSame(root, thrown.getCause());
//        verify(kafkaTemplate).send(topic, deviceId);
//    }


}
