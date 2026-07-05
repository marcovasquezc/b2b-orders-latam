package com.mariposa.orderworker.domain.model;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
public class Product {
    private final String productId;
    private final String name;
    private final String sku;
    private final String category;
    private final String taxCategory;
    private final String unitOfMeasure;
}