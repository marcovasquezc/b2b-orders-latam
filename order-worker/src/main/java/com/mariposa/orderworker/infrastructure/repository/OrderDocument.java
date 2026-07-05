package com.mariposa.orderworker.infrastructure.repository;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "enriched-orders")
public class OrderDocument {
    @Id
    private String id;
    private String orderId;
    private String status;
    private ClientDocument client;
    private List<ItemDocument> items;
    private SummaryDocument summary;
    private Instant processedAt;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClientDocument {
        private String clientId;
        private String name;
        private String segment;
        private String taxRegime;
        private String region;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemDocument {
        private String productId;
        private String name;
        private String sku;
        private String taxCategory;
        private int quantity;
        private java.math.BigDecimal unitPrice;
        private java.math.BigDecimal subtotal;
        private java.math.BigDecimal taxRate;
        private java.math.BigDecimal taxAmount;
        private java.math.BigDecimal lineTotal;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SummaryDocument {
        private java.math.BigDecimal subtotal;
        private java.math.BigDecimal totalTax;
        private java.math.BigDecimal grandTotal;
        private String currency;
    }
}