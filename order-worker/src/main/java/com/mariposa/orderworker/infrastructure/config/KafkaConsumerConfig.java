package com.mariposa.orderworker.infrastructure.config;

import com.mariposa.orderworker.infrastructure.dto.KafkaOrderInputDto;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ConsumerFactory<String, KafkaOrderInputDto> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "order-worker-group");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        JsonDeserializer<KafkaOrderInputDto> jsonDeserializer = new JsonDeserializer<>(KafkaOrderInputDto.class);
        jsonDeserializer.addTrustedPackages("*");
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), jsonDeserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, KafkaOrderInputDto> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, KafkaOrderInputDto> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        SimpleAsyncTaskExecutor loomExecutor = new SimpleAsyncTaskExecutor("kafka-loom-");
        loomExecutor.setVirtualThreads(true);
        factory.getContainerProperties().setListenerTaskExecutor(loomExecutor);

        return factory;
    }
}
