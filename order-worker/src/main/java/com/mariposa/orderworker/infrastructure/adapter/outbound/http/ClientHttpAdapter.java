package com.mariposa.orderworker.infrastructure.adapter.outbound.http;

import com.mariposa.orderworker.application.ports.outbound.ClientPort;
import com.mariposa.orderworker.domain.model.Client;
import com.mariposa.orderworker.infrastructure.dto.ClientResponseDto;
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
public class ClientHttpAdapter implements ClientPort {

    private final WebClient webClient;
    private final ReactiveHashOperations<String, String, ClientResponseDto> cacheOps;
    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final ReactiveCircuitBreakerFactory<?, ? extends ConfigBuilder<?>> cbFactory;
    private final long ttlSeconds;

    public ClientHttpAdapter(WebClient.Builder webClientBuilder,
                             @Value("${app.services.clients-url}") String url,
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
    public Mono<Client> getClientById(String clientId) {
        return cacheOps.get("clients-cache", clientId)
                .switchIfEmpty(Mono.defer(() -> fetchFromRemote(clientId)))
                .map(ClientResponseDto::toDomain);
    }

    private Mono<ClientResponseDto> fetchFromRemote(String clientId) {
        return webClient.get()
                .uri("/clients/{clientId}", clientId)
                .retrieve()
                .bodyToMono(ClientResponseDto.class)
                .transform(it -> cbFactory.create("externalApis").run(it, throwable -> Mono.error(new RuntimeException("API de Clientes no reactiva", throwable))))
                .flatMap(dto -> cacheOps.put("clients-cache", clientId, dto)
                        .then(redisTemplate.expire("clients-cache", Duration.ofSeconds(ttlSeconds)))
                        .thenReturn(dto));
    }
}
