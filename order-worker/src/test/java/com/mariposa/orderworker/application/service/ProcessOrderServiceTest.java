package com.mariposa.orderworker.application.service;

import com.mariposa.orderworker.application.ports.outbound.ClientPort;
import com.mariposa.orderworker.application.ports.outbound.OrderRepositoryPort;
import com.mariposa.orderworker.application.ports.outbound.ProductPort;
import com.mariposa.orderworker.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.List;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ProcessOrderServiceTest {

    @Mock
    private OrderRepositoryPort orderRepository;
    @Mock
    private ClientPort clientPort;
    @Mock
    private ProductPort productPort;

    private ProcessOrderService processOrderService;

    private Order sampleInputOrder;
    private Client sampleClient;
    private Product sampleProduct;

    @BeforeEach
    void setUp() {
        processOrderService = new ProcessOrderService(
                orderRepository,
                clientPort,
                productPort,
                Schedulers.immediate()
        );

        sampleClient = Client.builder()
                .clientId("CLI-002")
                .name("Comercializadora del Sur E.I.R.L.")
                .segment("MAYORISTA")
                .taxRegime("RESPONSABLE_IVA")
                .region("Arequipa")
                .channel("TRADICIONAL")
                .build();

        sampleProduct = Product.builder()
                .productId("PROD-001")
                .name("Gaseosa Inca Kola 3L")
                .sku("GAS-INK-3L")
                .category("Bebidas azucaradas")
                .taxCategory("GRAVADO")
                .unitOfMeasure("BOTELLA")
                .build();

        Item inputItem = Item.builder()
                .productId("PROD-001")
                .quantity(10)
                .unitPrice(new BigDecimal("2000.00"))
                .build();

        sampleInputOrder = Order.builder()
                .orderId("ORD-2024-COL-00147")
                .client(sampleClient)
                .items(List.of(inputItem))
                .build();
    }

    @Test
    @DisplayName("Should orchestrate enrichment and save order when it does not exist in DB")
    void shouldProcessAndSaveOrderSuccessfully() {
        // ARRANGE
        when(orderRepository.findById("ORD-2024-COL-00147")).thenReturn(Mono.empty());
        when(clientPort.getClientById("CLI-002")).thenReturn(Mono.just(sampleClient));
        when(productPort.getProductById("PROD-001")).thenReturn(Mono.just(sampleProduct));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        // ACT & ASSERT DE FLUJO REACTIVO
        StepVerifier.create(processOrderService.execute(sampleInputOrder))
                .verifyComplete();

        // ASSERTS DE COMPORTAMIENTO
        verify(orderRepository, times(1)).findById("ORD-2024-COL-00147");
        verify(clientPort, times(1)).getClientById("CLI-002");
        verify(productPort, times(1)).getProductById("PROD-001");
        verify(orderRepository, times(1)).save(any(Order.class));
    }

    @Test
    @DisplayName("Should skip processing when order already exists with PROCESSED status")
    void shouldSkipProcessingWhenOrderIsAlreadyProcessed() {
        // ARRANGE
        Order existingOrder = Order.builder()
                .orderId("ORD-2024-COL-00147")
                .status("PROCESSED")
                .build();

        when(orderRepository.findById("ORD-2024-COL-00147")).thenReturn(Mono.just(existingOrder));

        // ACT & ASSERT DE FLUJO REACTIVO
        StepVerifier.create(processOrderService.execute(sampleInputOrder))
                .verifyComplete();

        // ASSERTS DE COMPORTAMIENTO
        verify(orderRepository, times(1)).findById("ORD-2024-COL-00147");
        verifyNoInteractions(clientPort);
        verifyNoInteractions(productPort);
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("Should emit IllegalArgumentException when mandatory fields are missing")
    void shouldFailWhenOrderIsInvalid() {
        // ARRANGE
        Order invalidOrder = Order.builder()
                .orderId(null)
                .build();

        // ACT & ASSERT DE FLUJO REACTIVO
        StepVerifier.create(processOrderService.execute(invalidOrder))
                .expectError(IllegalArgumentException.class)
                .verify();

        // ASSERTS DE COMPORTAMIENTO
        verifyNoInteractions(orderRepository);
        verifyNoInteractions(clientPort);
        verifyNoInteractions(productPort);
    }
}
