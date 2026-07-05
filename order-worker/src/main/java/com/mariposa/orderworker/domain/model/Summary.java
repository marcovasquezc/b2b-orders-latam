package com.mariposa.orderworker.domain.model;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import java.math.BigDecimal;

@Getter
@Builder
@ToString
public class Summary {
    private final BigDecimal subtotal;
    private final BigDecimal totalTax;
    private final BigDecimal grandTotal;
    private final String currency;
}
