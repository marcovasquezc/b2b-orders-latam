package com.mariposa.orderworker.infrastructure.adapter.outbound.http;

import com.mariposa.orderworker.application.ports.outbound.ProductPort;
import com.mariposa.orderworker.domain.model.Product;
import com.mariposa.orderworker.infrastructure.dto.ProductResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.ConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import java.time.Duration;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;

@Slf4j
@Component
public class ProductHttpAdapter implements ProductPort {

    private final WebClient webClient;
    private final ReactiveHashOperations<String, String, ProductResponseDto> cacheOps;
    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final ReactiveCircuitBreakerFactory<?, ? extends ConfigBuilder<?>> cbFactory;
    private final Retry retryInstance;
    private final long ttlSeconds;

    public ProductHttpAdapter(WebClient.Builder webClientBuilder,
                              @Value("${app.services.products-url}") String url,
                              ReactiveRedisTemplate<String, Object> redisTemplate,
                              @Qualifier("productCacheOps") ReactiveHashOperations<String, String, ProductResponseDto> cacheOps,
                              ReactiveCircuitBreakerFactory<?, ? extends ConfigBuilder<?>> cbFactory,
                              RetryRegistry retryRegistry,
                              @Value("${app.cache.ttl-seconds:300}") long ttlSeconds) {
        this.webClient = webClientBuilder.baseUrl(url).build();
        this.redisTemplate = redisTemplate;
        this.cacheOps = cacheOps;
        this.cbFactory = cbFactory;
        this.ttlSeconds = ttlSeconds;
        this.retryInstance = retryRegistry.retry("productsApi");

        this.retryInstance.getEventPublisher()
                .onRetry(event -> log.warn("[RETRY-PRODUCTS] Intentando reconexión con API de Productos. Intento actual: #{} | Espera: {}ms",
                        event.getNumberOfRetryAttempts(), event.getWaitInterval().toMillis()))
                .onError(event -> log.error("Se agotaron definitivamente los intentos del YAML para la API de Productos."));
    }

    @Override
    public Mono<Product> getProductById(String productId) {
        return cacheOps.get("products-cache", productId)
                .switchIfEmpty(Mono.defer(() -> fetchFromRemote(productId)))
                .map(ProductResponseDto::toDomain);
    }

    private Mono<ProductResponseDto> fetchFromRemote(String productId) {
        log.info("El producto {} no se encontró en Redis. Invocando API remota...", productId);

        Mono<ProductResponseDto> pipeline = webClient.get()
                .uri("/products/{productId}", productId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse ->
                        Mono.error(new WebClientResponseException(
                                clientResponse.statusCode().value(), "Error HTTP detectado en API", null, null, null, null)))
                .bodyToMono(ProductResponseDto.class)
                .transform(it -> cbFactory.create("productsApi").run(it, throwable -> Mono.error(new RuntimeException("API de Productos no reactiva", throwable))));

        return pipeline
                .transformDeferred(RetryOperator.of(retryInstance))
                .flatMap(dto -> cacheOps.put("products-cache", productId, dto)
                        .then(redisTemplate.expire("products-cache", Duration.ofSeconds(ttlSeconds)))
                        .thenReturn(dto));
    }
}