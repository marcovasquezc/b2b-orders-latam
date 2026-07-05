package com.mariposa.orderworker.application.ports.outbound;

import com.mariposa.orderworker.domain.model.Product;
import reactor.core.publisher.Mono;

public interface ProductPort {
    Mono<Product> getProductById(String productId);
}