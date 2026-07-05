package com.mariposa.orderworker.infrastructure.adapter.inbound.kafka;

import com.mariposa.orderworker.application.ports.inbound.ProcessOrderUseCase;
import com.mariposa.orderworker.infrastructure.dto.KafkaOrderInputDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Schedulers;
import java.time.Instant;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderKafkaListener {

    private final ProcessOrderUseCase processOrderUseCase;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${spring.kafka.consumer.properties.orders-dlt-topic}")
    private String dltTopicName;

    @KafkaListener(topics = "${spring.kafka.consumer.properties.orders-topic}")
    public void listen(KafkaOrderInputDto inputDto) {
        MDC.put("orderId", inputDto.orderId());

        log.info("Mensaje capturado en Kafka para procesar la orden: {}", inputDto.orderId());

        processOrderUseCase.execute(inputDto.toDomain())
                .subscribeOn(Schedulers.boundedElastic())
                .doFinally(signal -> MDC.clear())
                .subscribe(
                        unused -> log.info("Pipeline reactivo finalizado con éxito para orden: {}", inputDto.orderId()),
                        error -> sendToDeadLetterTopic(inputDto, error)
                );
    }

    private void sendToDeadLetterTopic(KafkaOrderInputDto inputDto, Throwable error) {
        log.error("Fallo crítico e irrecuperable. Redireccionando orden {} a DLT.", inputDto.orderId(), error);

        Map<String, Object> payloadDLT = Map.of(
                "timestamp", Instant.now().toString(),
                "orderId", inputDto.orderId(),
                "errorMessage", error.getMessage(),
                "originalPayload", inputDto
        );

        kafkaTemplate.send(dltTopicName, inputDto.orderId(), payloadDLT);
    }
}