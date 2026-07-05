package com.mariposa.orderworker.domain.model;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import java.math.BigDecimal;

@Getter
@Builder
@ToString
public class Item {
    private final String productId;
    private final String name;
    private final String sku;
    private final String taxCategory;
    private final int quantity;
    private final BigDecimal unitPrice;
    private final BigDecimal subtotal;
    private final BigDecimal taxRate;
    private final BigDecimal taxAmount;
    private final BigDecimal lineTotal;
}
