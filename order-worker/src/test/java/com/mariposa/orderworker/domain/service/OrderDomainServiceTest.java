package com.mariposa.orderworker.domain.service;

import com.mariposa.orderworker.domain.model.Client;
import com.mariposa.orderworker.domain.model.Item;
import com.mariposa.orderworker.domain.model.Order;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class OrderDomainServiceTest {
    private OrderDomainService orderDomainService;
    private Client sampleClient;

    @BeforeEach
    void setUp() {
        orderDomainService = new OrderDomainService();

        sampleClient = Client.builder()
                .clientId("CLI-002")
                .name("Comercializadora del Sur E.I.R.L.")
                .segment("MAYORISTA")
                .taxRegime("RESPONSABLE_IVA")
                .region("Arequipa")
                .channel("TRADICIONAL")
                .build();
    }

    @Test
    @DisplayName("It should correctly calculate the tax per line item, including rounding")
    void shouldCalculateTaxPerItemLineCorrectly() {
        // ARRANGE
        int quantity = 3;

        BigDecimal unitPrice = new BigDecimal("10505.535");
        String productId = "PROD-003";
        String name = "Chocolate Sublime Clásico 26g";
        String sku = "SUB-CLA-26G";
        String taxCategory = "GRAVADO";

        // ACT
        Item calculatedItem = orderDomainService.calculateTax(quantity, unitPrice, productId, name, sku, taxCategory);

        // ASSERT
        assertNotNull(calculatedItem);
        assertEquals(new BigDecimal("10505.54"), calculatedItem.getUnitPrice());
        assertEquals(new BigDecimal("31516.61"), calculatedItem.getSubtotal());
        assertEquals(new BigDecimal("5988.16"), calculatedItem.getTaxAmount());
        assertEquals(new BigDecimal("37504.77"), calculatedItem.getLineTotal());
        assertEquals("GRAVADO", calculatedItem.getTaxCategory());
    }

    @Test
    @DisplayName("It should build the order by consolidating subtotals and the summary.")
    void shouldBuildOrderAndConsolidateTotalsInSummary() {
        // ARRANGE
        Item itemGravado = orderDomainService.calculateTax(2, new BigDecimal("5000.00"), "P1", "Prod Gravado", "SKU1", "GRAVADO");   // Sub: 10000.00, Tax: 1900.00
        Item itemReducido = orderDomainService.calculateTax(5, new BigDecimal("2000.00"), "P2", "Prod Reducido", "SKU2", "REDUCIDO"); // Sub: 10000.00, Tax: 500.00
        Item itemExento = orderDomainService.calculateTax(1, new BigDecimal("3500.00"), "P3", "Prod Exento", "SKU3", "EXENTO");     // Sub: 3500.00,  Tax: 0.00

        List<Item> calculatedItems = List.of(itemGravado, itemReducido, itemExento);
        String expectedOrderId = "ORD-2024-COL-00147";

        // ACT
        Order processedOrder = orderDomainService.buildOrder(expectedOrderId, sampleClient, calculatedItems);

        // ASSERT
        assertNotNull(processedOrder);
        assertEquals("PROCESSED", processedOrder.getStatus());
        assertEquals(expectedOrderId, processedOrder.getOrderId());
        assertEquals(sampleClient, processedOrder.getClient());
        assertNotNull(processedOrder.getProcessedAt());
        assertEquals(3, processedOrder.getItems().size());
        assertNotNull(processedOrder.getSummary());
        assertEquals("COP", processedOrder.getSummary().getCurrency());
        assertEquals(new BigDecimal("23500.00"), processedOrder.getSummary().getSubtotal());
        assertEquals(new BigDecimal("2400.00"), processedOrder.getSummary().getTotalTax());
        assertEquals(new BigDecimal("25900.00"), processedOrder.getSummary().getGrandTotal());
    }

    @Test
    @DisplayName("It should apply the GRAVADO rate by default if a non-existent or unknown category is entered")
    void shouldApplyDefaultTaxWhenCategoryIsUnknown() {
        // ARRANGE
        String unknownCategory = "CATEGORIA_INVENTADA";

        // ACT
        Item calculatedItem = orderDomainService.calculateTax(
                1,
                new BigDecimal("1000.00"),
                "PROD-001",
                "Gaseosa Inca Kola 3L",
                "GAS-INK-3L", unknownCategory
        );

        // ASSERT
        assertEquals(new BigDecimal("190.00"), calculatedItem.getTaxAmount());
        assertEquals(new BigDecimal("1190.00"), calculatedItem.getLineTotal());
        assertEquals("CATEGORIA_INVENTADA", calculatedItem.getTaxCategory());
    }
}
