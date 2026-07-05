package com.mariposa.orderworker.infrastructure.config;

import com.mariposa.orderworker.infrastructure.dto.ClientResponseDto;
import com.mariposa.orderworker.infrastructure.dto.ProductResponseDto;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisReactiveConfig {
    @Bean
    public ReactiveRedisTemplate<String, Object> reactiveRedisTemplate(ReactiveRedisConnectionFactory factory) {
        Jackson2JsonRedisSerializer<Object> serializer = new Jackson2JsonRedisSerializer<>(Object.class);
        RedisSerializationContext.RedisSerializationContextBuilder<String, Object> builder =
                RedisSerializationContext.newSerializationContext(new StringRedisSerializer());
        return new ReactiveRedisTemplate<>(factory, builder.value(serializer).hashValue(serializer).build());
    }

    @Bean
    public ReactiveHashOperations<String, String, ProductResponseDto> productCacheOps(ReactiveRedisConnectionFactory factory) {
        Jackson2JsonRedisSerializer<ProductResponseDto> serializer = new Jackson2JsonRedisSerializer<>(ProductResponseDto.class);
        return new ReactiveRedisTemplate<>(factory, RedisSerializationContext.<String, ProductResponseDto>newSerializationContext(new StringRedisSerializer()).hashValue(serializer).build()).opsForHash();
    }

    @Bean
    public ReactiveHashOperations<String, String, ClientResponseDto> clientCacheOps(ReactiveRedisConnectionFactory factory) {
        Jackson2JsonRedisSerializer<ClientResponseDto> serializer = new Jackson2JsonRedisSerializer<>(ClientResponseDto.class);
        return new ReactiveRedisTemplate<>(factory, RedisSerializationContext.<String, ClientResponseDto>newSerializationContext(new StringRedisSerializer()).hashValue(serializer).build()).opsForHash();
    }
}