package io.ussopmm.eventcollectorservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

import java.util.List;

@Configuration
public class KafkaTopicConfig {

    @Value("${spring.kafka.first-topic.name}")
    private String deviceIdTopicName;

    @Value("${spring.kafka.second-topic.name}")
    private String eventsTopicName;

    @Bean
    public List<NewTopic> kafkaTopics() {
        return List.of(
                TopicBuilder.name(deviceIdTopicName)
                        .build(),
                TopicBuilder.name(eventsTopicName)
                        .build()
        );
    }

}