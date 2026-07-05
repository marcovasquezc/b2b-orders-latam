package com.mariposa.orderworker.application.service;

import com.mariposa.orderworker.application.ports.inbound.ProcessOrderUseCase;
import com.mariposa.orderworker.application.ports.outbound.ClientPort;
import com.mariposa.orderworker.application.ports.outbound.OrderRepositoryPort;
import com.mariposa.orderworker.application.ports.outbound.ProductPort;
import com.mariposa.orderworker.domain.model.Client;
import com.mariposa.orderworker.domain.model.Item;
import com.mariposa.orderworker.domain.model.Order;
import com.mariposa.orderworker.domain.service.OrderDomainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProcessOrderService implements ProcessOrderUseCase {
    private final OrderRepositoryPort orderRepository;
    private final ClientPort clientPort;
    private final ProductPort productPort;
    private final OrderDomainService orderDomainService = new OrderDomainService();

    @Override
    public Mono<Void> execute(Order order) {
        return Mono.defer(() -> {
            log.info("Procesando pedido ID: {}", order.getOrderId());

            if (isOrderInvalid(order)) {
                return Mono.error(new IllegalArgumentException("Mensaje inválido: Faltan campos obligatorios en la orden."));
            }

            return orderRepository.findById(order.getOrderId())
                    .map(existingOrder -> "PROCESSED".equals(existingOrder.getStatus()))
                    .defaultIfEmpty(false)
                    .flatMap(isProcessed -> {
                        if (isProcessed) {
                            log.info("Idempotencia detectada para pedido {}. Omitiendo duplicado.", order.getOrderId());

                            return Mono.empty();
                        } else {
                            return runPipeline(order).then();
                        }
                    });
        })
        .contextWrite(context -> order != null && order.getOrderId() != null
                ? context.put("orderId", order.getOrderId())
                : context);
    }

    private Mono<Order> runPipeline(Order order) {
        Mono<Client> client = clientPort.getClientById(order.getClient().getClientId());

        Mono<List<Item>> items = Flux.fromIterable(order.getItems())
                .flatMap(currentItem -> productPort.getProductById(currentItem.getProductId())
                        .map(product -> orderDomainService.calculateTax(
                                currentItem.getQuantity(),
                                currentItem.getUnitPrice(),
                                product.getProductId(),
                                product.getName(),
                                product.getSku(),
                                product.getTaxCategory()
                        ))
                )
                .collectList();

        return Mono.zip(client, items)
                .map(tuple -> orderDomainService.buildOrder(order.getOrderId(), tuple.getT1(), tuple.getT2()))
                .flatMap(orderRepository::save)
                .doOnSuccess(saved -> log.info("Pedido {} guardado en MongoDB con éxito.", saved.getOrderId()));
    }

    private boolean isOrderInvalid(Order order) {
        return order == null || order.getOrderId() == null
                || order.getClient() == null || order.getClient().getClientId() == null
                || order.getItems() == null || order.getItems().isEmpty();
    }
}