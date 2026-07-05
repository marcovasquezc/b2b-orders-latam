package com.mariposa.orderworker.application.ports.inbound;

import com.mariposa.orderworker.domain.model.Order;
import reactor.core.publisher.Mono;

public interface ProcessOrderUseCase {
    Mono<Void> execute(Order order);
}
