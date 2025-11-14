package io.ussopmm.device_collector_service.config;

import com.nashkod.avro.Device;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

@Configuration
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
@Profile("!test") // Не загружать в тестах - там своя конфигурация
public class KafkaConsumerConfig {

    @Value("${spring.kafka.concurrency:1}")
    private int concurrency;

    @Value("${spring.kafka.poll-timeout:2000}")
    private int pollTimeout;

    @Value("${spring.kafka.batch-listener-enabled:false}")
    private boolean batchListenerEnabled;

    @Bean(name = "kafkaConsumerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, Device> kafkaListenerContainerFactory(
            ConsumerFactory<String, Device> consumerFactory
    ) {
        ConcurrentKafkaListenerContainerFactory<String, Device> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConcurrency(concurrency);
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setPollTimeout(pollTimeout);
        factory.setBatchListener(batchListenerEnabled);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }
}
