package com.mariposa.orderworker.infrastructure.adapter.inbound.kafka;

import com.mariposa.orderworker.infrastructure.dto.KafkaOrderInputDto;
import com.mariposa.orderworker.infrastructure.dto.ClientResponseDto;
import com.mariposa.orderworker.infrastructure.dto.ProductResponseDto;
import com.mariposa.orderworker.infrastructure.repository.OrderDocument;
import com.mariposa.orderworker.domain.model.Product;
import com.mariposa.orderworker.domain.model.Client;
import com.mariposa.orderworker.application.ports.outbound.ProductPort;
import com.mariposa.orderworker.application.ports.outbound.ClientPort;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// Se desactivan los hilos virtuales globales EXCLUSIVAMENTE en el entorno de pruebas
// para estabilizar el ciclo de vida de JUnit y mitigar condiciones de carrera asíncronas con Testcontainers.
// En producción (application.yml), permanecen activos (true) para maximizar el rendimiento transaccional.
@SpringBootTest(properties = {
        "spring.main.allow-bean-definition-overriding=true",
        "spring.threads.virtual.enabled=false",
        "app.services.clients-url=http://localhost:8081",
        "app.services.products-url=http://localhost:8082"
})
@Testcontainers
@Import(ProcessOrderServiceIntegrationTest.TestSchedulerConfig.class)
class ProcessOrderServiceIntegrationTest {
    @TestConfiguration
    static class TestSchedulerConfig {
        @Bean(name = "virtualThreadScheduler")
        public Scheduler testVirtualThreadScheduler() {
            return Schedulers.immediate();
        }
    }

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:6.0.5"));

    @Container
    static KafkaContainer kafkaContainer = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    private static final String TEST_TOPIC = "orders-integration-topic";

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
        registry.add("spring.kafka.bootstrap-servers", kafkaContainer::getBootstrapServers);
        registry.add("spring.kafka.consumer.properties.orders-topic", () -> TEST_TOPIC);
        registry.add("spring.kafka.consumer.properties.orders-dlt-topic", () -> "orders-integration-dlt");
        registry.add("spring.kafka.consumer.properties.spring.json.trusted.packages", () -> "*");
    }

    @MockBean
    private ProductPort productPort;

    @MockBean
    private ClientPort clientPort;

    @MockBean
    private org.springframework.data.redis.core.ReactiveRedisTemplate<String, Object> reactiveRedisTemplate;

    @MockBean
    private org.springframework.data.redis.core.ReactiveHashOperations<String, String, ProductResponseDto> productCacheOps;

    @MockBean
    private org.springframework.data.redis.core.ReactiveHashOperations<String, String, ClientResponseDto> clientCacheOps;

    @MockBean
    private org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory<?, ?> cbFactory;

    @Autowired
    private ReactiveMongoTemplate reactiveMongoTemplate;

    private static KafkaTemplate<String, KafkaOrderInputDto> testKafkaTemplate;

    @BeforeAll
    static void initProducer() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        testKafkaTemplate = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(configProps));
    }

    @BeforeEach
    void setupReactiveMocks() {
        Product mockProduct = Product.builder()
                .productId("PROD-001")
                .name("Gaseosa 600ml")
                .sku("GAS-600-PET")
                .category("BEBIDAS")
                .taxCategory("GRAVADO")
                .unitOfMeasure("UNIDAD")
                .build();

        Client mockClient = Client.builder()
                .clientId("CLI-002")
                .name("Distribuidora Andina S.A.S")
                .segment("MAYORISTA")
                .taxRegime("RESPONSABLE_IVA")
                .region("Valle del Cauca")
                .channel("B2B")
                .build();

        Mockito.when(productPort.getProductById("PROD-001")).thenReturn(Mono.just(mockProduct));
        Mockito.when(clientPort.getClientById("CLI-002")).thenReturn(Mono.just(mockClient));
    }

    @Test
    @DisplayName("E2E Integration: Should ingest event from Real Kafka and persist in real enriched-orders collection")
    void shouldIngestFromKafkaAndPersistInMongoRealInstance() {
        // ARRANGE
        String testOrderId = "ORD-INT-TEST-2026";
        KafkaOrderInputDto inputDto = new KafkaOrderInputDto(
                testOrderId,
                "CLI-002",
                "B2B",
                java.time.Instant.now().toString(),
                List.of(new KafkaOrderInputDto.KafkaItemInputDto("PROD-001", 5, new BigDecimal("120.00")))
        );

        // ACT
        testKafkaTemplate.send(TEST_TOPIC, testOrderId, inputDto);

        // ASSERT
        Query query = Query.query(Criteria.where("orderId").is(testOrderId));

        Mono<OrderDocument> mongoQueryMono = reactiveMongoTemplate.findOne(query, OrderDocument.class)
                .repeatWhenEmpty(60, maxRepeatFlux -> maxRepeatFlux.delayElements(Duration.ofMillis(300)))
                .timeout(Duration.ofSeconds(25));

        StepVerifier.create(mongoQueryMono)
                .assertNext(savedDoc -> {
                    assertThat(savedDoc).isNotNull();
                    assertThat(savedDoc.getOrderId()).isEqualTo(testOrderId);
                    assertThat(savedDoc.getStatus()).isEqualTo("PROCESSED");

                    assertThat(savedDoc.getClient()).isNotNull();
                    assertThat(savedDoc.getClient().getName()).isEqualTo("Distribuidora Andina S.A.S");
                    assertThat(savedDoc.getClient().getSegment()).isEqualTo("MAYORISTA");

                    assertThat(savedDoc.getItems()).isNotEmpty();
                    OrderDocument.ItemDocument primaryItem = savedDoc.getItems().getFirst();
                    assertThat(primaryItem.getName()).isEqualTo("Gaseosa 600ml");
                    assertThat(primaryItem.getProductId()).isEqualTo("PROD-001");
                })
                .verifyComplete();
    }
}