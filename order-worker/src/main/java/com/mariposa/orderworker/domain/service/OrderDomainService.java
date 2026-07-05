package com.mariposa.orderworker.domain.service;

import com.mariposa.orderworker.domain.model.Client;
import com.mariposa.orderworker.domain.model.Order;
import com.mariposa.orderworker.domain.model.Item;
import com.mariposa.orderworker.domain.model.Summary;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

public class OrderDomainService {

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    
    public Item calculateTax(int quantity, BigDecimal unitPrice, String productId, String name, String sku, String taxCategory) {
        BigDecimal qty = BigDecimal.valueOf(quantity);
        BigDecimal taxRate = getTaxRateByCategory(taxCategory);

        BigDecimal subtotal = qty.multiply(unitPrice).setScale(SCALE, ROUNDING);
        BigDecimal taxAmount = subtotal.multiply(taxRate).setScale(SCALE, ROUNDING);
        BigDecimal lineTotal = subtotal.add(taxAmount).setScale(SCALE, ROUNDING);

        return Item.builder()
                .productId(productId)
                .name(name)
                .sku(sku)
                .taxCategory(taxCategory.toUpperCase())
                .quantity(quantity)
                .unitPrice(unitPrice.setScale(SCALE, ROUNDING))
                .subtotal(subtotal)
                .taxRate(taxRate)
                .taxAmount(taxAmount)
                .lineTotal(lineTotal)
                .build();
    }

    public Order buildOrder(String orderId, Client client, List<Item> items) {
        BigDecimal totalSubtotal = BigDecimal.ZERO.setScale(SCALE, ROUNDING);
        BigDecimal totalTax = BigDecimal.ZERO.setScale(SCALE, ROUNDING);

        for (Item item : items) {
            totalSubtotal = totalSubtotal.add(item.getSubtotal());
            totalTax = totalTax.add(item.getTaxAmount());
        }

        BigDecimal grandTotal = totalSubtotal.add(totalTax).setScale(SCALE, ROUNDING);

        Summary summary = Summary.builder()
                .subtotal(totalSubtotal)
                .totalTax(totalTax)
                .grandTotal(grandTotal)
                .currency("COP")
                .build();

        return Order.builder()
                .orderId(orderId)
                .status("PROCESSED")
                .client(client)
                .items(items)
                .summary(summary)
                .processedAt(Instant.now())
                .build();
    }

    private BigDecimal getTaxRateByCategory(String category) {
        return switch (category.toUpperCase().trim()) {
            case "GRAVADO" -> new BigDecimal("0.19");
            case "REDUCIDO" -> new BigDecimal("0.05");
            case "EXENTO" -> new BigDecimal("0.00");
            default -> new BigDecimal("0.19");
        };
    }
}
