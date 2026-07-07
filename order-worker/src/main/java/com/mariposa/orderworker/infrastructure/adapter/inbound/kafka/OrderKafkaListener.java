package com.mariposa.orderworker.infrastructure.adapter.inbound.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mariposa.orderworker.application.ports.inbound.ProcessOrderUseCase;
import com.mariposa.orderworker.infrastructure.dto.KafkaOrderInputDto;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Scheduler;
import java.time.Instant;
import java.util.Map;

@Slf4j
@Component
public class OrderKafkaListener {

    private final ProcessOrderUseCase processOrderUseCase;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final Scheduler virtualThreadScheduler;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RetryRegistry retryRegistry;

    @Value("${spring.kafka.consumer.properties.orders-dlt-topic}")
    private String dltTopicName;

    public OrderKafkaListener(
            ProcessOrderUseCase processOrderUseCase,
            KafkaTemplate<String, Object> kafkaTemplate,
            @Qualifier("virtualThreadScheduler") Scheduler virtualThreadScheduler,
            RetryRegistry retryRegistry) {
        this.processOrderUseCase = processOrderUseCase;
        this.kafkaTemplate = kafkaTemplate;
        this.virtualThreadScheduler = virtualThreadScheduler;
        this.retryRegistry = retryRegistry;
    }

    @KafkaListener(topics = "${spring.kafka.consumer.properties.orders-topic}")
    public void listen(KafkaOrderInputDto inputDto) {
        MDC.put("orderId", inputDto.orderId());

        log.info("Mensaje capturado en Kafka para procesar la orden: {}", inputDto.orderId());

        processOrderUseCase.execute(inputDto.toDomain())
                .subscribeOn(virtualThreadScheduler)
                .doFinally(signal -> MDC.clear())
                .subscribe(
                        unused -> log.info("Pipeline reactivo finalizado con éxito para orden: {}", inputDto.orderId()),
                        error -> {
                            int totalAttempts = retryRegistry.retry("externalApis")
                                    .getRetryConfig()
                                    .getMaxAttempts();

                            sendToDeadLetterTopic(inputDto, error, totalAttempts);
                        }
                );
    }

    private void sendToDeadLetterTopic(KafkaOrderInputDto inputDto, Throwable error, int attemptNumber) {
        log.error("Fallo crítico tras agotar reintentos. Redireccionando orden {} a DLT.", inputDto.orderId(), error);

        Throwable rootCause = error.getCause() != null ? error.getCause() : error;

        Map<String, Object> payloadDLT = Map.of(
                "timestamp", Instant.now().toString(),
                "causa", rootCause.getMessage() != null ? rootCause.getMessage() : "Error desconocido",
                "intentos", attemptNumber,
                "orderId", inputDto.orderId(),
                "originalPayload", inputDto
        );

        try {
            String jsonStringPayload = objectMapper.writeValueAsString(payloadDLT);

            kafkaTemplate.send(dltTopicName, inputDto.orderId(), jsonStringPayload);

            log.info("Metadata de error para orden {} enviada a DLT con éxito.", inputDto.orderId());
        } catch (JsonProcessingException e) {
            log.error("Error catastrófico de serialización al construir reporte DLT para la orden {}", inputDto.orderId(), e);

            kafkaTemplate.send(dltTopicName, inputDto.orderId(), payloadDLT.toString());
        }
    }
}
