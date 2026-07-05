package com.mariposa.orderworker.infrastructure.dto;

import com.mariposa.orderworker.domain.model.Product;

public record ProductResponseDto(
        String productId,
        String name,
        String sku,
        String category,
        String taxCategory,
        String unitOfMeasure
) {
    public Product toDomain() {
        return Product.builder()
                .productId(this.productId)
                .name(this.name)
                .sku(this.sku)
                .category(this.category)
                .taxCategory(this.taxCategory)
                .unitOfMeasure(this.unitOfMeasure)
                .build();
    }
}
