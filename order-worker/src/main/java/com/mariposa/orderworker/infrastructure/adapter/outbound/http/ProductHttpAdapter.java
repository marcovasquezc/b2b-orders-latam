package com.mariposa.orderworker.infrastructure.adapter.outbound.http;

import com.mariposa.orderworker.application.ports.outbound.ProductPort;
import com.mariposa.orderworker.domain.model.Product;
import com.mariposa.orderworker.infrastructure.dto.ProductResponseDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.ConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import java.time.Duration;

@Component
public class ProductHttpAdapter implements ProductPort {

    private final WebClient webClient;
    private final ReactiveHashOperations<String, String, ProductResponseDto> cacheOps;
    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final ReactiveCircuitBreakerFactory<?, ? extends ConfigBuilder<?>> cbFactory;
    private final long ttlSeconds;

    public ProductHttpAdapter(WebClient.Builder webClientBuilder,
                              @Value("${app.services.products-url}") String url,
                              ReactiveRedisTemplate<String, Object> redisTemplate,
                              ReactiveCircuitBreakerFactory<?, ? extends ConfigBuilder<?>> cbFactory,
                              @Value("${app.cache.ttl-seconds:300}") long ttlSeconds) {
        this.webClient = webClientBuilder.baseUrl(url).build();
        this.redisTemplate = redisTemplate;
        this.cacheOps = redisTemplate.opsForHash();
        this.cbFactory = cbFactory;
        this.ttlSeconds = ttlSeconds;
    }

    @Override
    public Mono<Product> getProductById(String productId) {
        return cacheOps.get("products-cache", productId)
                .switchIfEmpty(Mono.defer(() -> fetchFromRemote(productId)))
                .map(ProductResponseDto::toDomain);
    }

    private Mono<ProductResponseDto> fetchFromRemote(String productId) {
        return webClient.get()
                .uri("/products/{productId}", productId)
                .retrieve()
                .bodyToMono(ProductResponseDto.class)
                .transform(it -> cbFactory.create("externalApis").run(it, throwable -> Mono.error(new RuntimeException("API de Productos no reactiva", throwable))))
                .flatMap(dto -> cacheOps.put("products-cache", productId, dto)
                        .then(redisTemplate.expire("products-cache", Duration.ofSeconds(ttlSeconds)))
                        .thenReturn(dto));
    }
}
