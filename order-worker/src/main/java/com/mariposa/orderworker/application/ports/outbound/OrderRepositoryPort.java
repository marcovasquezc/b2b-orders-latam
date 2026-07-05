package com.mariposa.orderworker.application.ports.outbound;

import com.mariposa.orderworker.domain.model.Order;
import reactor.core.publisher.Mono;

public interface OrderRepositoryPort {
    Mono<Order> findById(String orderId);
    Mono<Order> save(Order order);
}
