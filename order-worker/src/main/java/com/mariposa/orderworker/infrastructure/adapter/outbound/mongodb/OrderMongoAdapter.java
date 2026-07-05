package com.mariposa.orderworker.infrastructure.adapter.outbound.mongodb;

import com.mariposa.orderworker.application.ports.outbound.OrderRepositoryPort;
import com.mariposa.orderworker.domain.model.Order;
import com.mariposa.orderworker.infrastructure.mapper.OrderDocumentMapper;
import com.mariposa.orderworker.infrastructure.repository.SpringDataMongoOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class OrderMongoAdapter implements OrderRepositoryPort {

    private final SpringDataMongoOrderRepository repository;
    private final OrderDocumentMapper mapper;

    @Override
    public Mono<Order> findById(String orderId) {
        return repository.findByOrderId(orderId)
                .map(doc -> Order.builder()
                        .orderId(doc.getOrderId())
                        .status(doc.getStatus())
                        .build());
    }

    @Override
    public Mono<Order> save(Order order) {
        return repository.save(mapper.toDocument(order)).thenReturn(order);
    }
}