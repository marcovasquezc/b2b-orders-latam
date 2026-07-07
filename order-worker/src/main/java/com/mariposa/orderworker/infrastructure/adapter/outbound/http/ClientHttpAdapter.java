package com.mariposa.orderworker.infrastructure.adapter.outbound.http;

import com.mariposa.orderworker.application.ports.outbound.ClientPort;
import com.mariposa.orderworker.domain.model.Client;
import com.mariposa.orderworker.infrastructure.dto.ClientResponseDto;
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
public class ClientHttpAdapter implements ClientPort {

    private final WebClient webClient;
    private final ReactiveHashOperations<String, String, ClientResponseDto> cacheOps;
    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final ReactiveCircuitBreakerFactory<?, ? extends ConfigBuilder<?>> cbFactory;
    private final Retry retryInstance;
    private final long ttlSeconds;

    public ClientHttpAdapter(WebClient.Builder webClientBuilder,
                             @Value("${app.services.clients-url}") String url,
                             ReactiveRedisTemplate<String, Object> redisTemplate,
                             @Qualifier("clientCacheOps") ReactiveHashOperations<String, String, ClientResponseDto> cacheOps,
                             ReactiveCircuitBreakerFactory<?, ? extends ConfigBuilder<?>> cbFactory,
                             RetryRegistry retryRegistry,
                             @Value("${app.cache.ttl-seconds:300}") long ttlSeconds) {
        this.webClient = webClientBuilder.baseUrl(url).build();
        this.redisTemplate = redisTemplate;
        this.cacheOps = cacheOps;
        this.cbFactory = cbFactory;
        this.ttlSeconds = ttlSeconds;
        this.retryInstance = retryRegistry.retry("clientsApi");

        this.retryInstance.getEventPublisher()
                .onRetry(event -> log.warn("[RETRY-CLIENTS] Intentando reconexión con API de Clientes. Intento actual: #{} | Espera: {}ms",
                        event.getNumberOfRetryAttempts(), event.getWaitInterval().toMillis()))
                .onError(event -> log.error("Se agotaron definitivamente los intentos del YAML para la API de Clientes."));
    }

    @Override
    public Mono<Client> getClientById(String clientId) {
        return cacheOps.get("clients-cache", clientId)
                .switchIfEmpty(Mono.defer(() -> fetchFromRemote(clientId)))
                .map(ClientResponseDto::toDomain);
    }

    private Mono<ClientResponseDto> fetchFromRemote(String clientId) {
        log.info("El cliente {} no se encontró en Redis. Invocando API remota...", clientId);

        Mono<ClientResponseDto> pipeline = webClient.get()
                .uri("/clients/{clientId}", clientId)
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse ->
                        Mono.error(new WebClientResponseException(
                                clientResponse.statusCode().value(), "Error HTTP detectado en API", null, null, null, null)))
                .bodyToMono(ClientResponseDto.class)
                .transform(it -> cbFactory.create("clientsApi").run(it, throwable -> Mono.error(new RuntimeException("API de Clientes no reactiva", throwable))));

        return pipeline
                .transformDeferred(RetryOperator.of(retryInstance))
                .flatMap(dto -> cacheOps.put("clients-cache", clientId, dto)
                        .then(redisTemplate.expire("clients-cache", Duration.ofSeconds(ttlSeconds)))
                        .thenReturn(dto));
    }
}
