package com.mariposa.orderworker.infrastructure.dto;

import com.mariposa.orderworker.domain.model.Client;
import com.mariposa.orderworker.domain.model.Item;
import com.mariposa.orderworker.domain.model.Order;
import java.math.BigDecimal;
import java.util.List;

public record KafkaOrderInputDto(
        String orderId,
        String clientId,
        String channel,
        String createdAt,
        List<KafkaItemInputDto> items
) {
    public record KafkaItemInputDto(
            String productId,
            int quantity,
            BigDecimal unitPrice
    ) {}

    public Order toDomain() {
        Client client = Client.builder()
                .clientId(this.clientId)
                .build();

        List<Item> items = this.items.stream()
                .map(i -> Item.builder()
                        .productId(i.productId())
                        .quantity(i.quantity())
                        .unitPrice(i.unitPrice())
                        .build())
                .toList();

        return Order.builder()
                .orderId(this.orderId)
                .client(client)
                .items(items)
                .build();
    }
}
