package com.mariposa.orderworker.infrastructure.repository;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface SpringDataMongoOrderRepository extends ReactiveMongoRepository<OrderDocument, String> {
    Mono<OrderDocument> findByOrderId(String orderId);
}