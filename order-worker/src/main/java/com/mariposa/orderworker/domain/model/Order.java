package com.mariposa.orderworker.domain.model;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import java.time.Instant;
import java.util.List;

@Getter
@Builder
@ToString
public class Order {
    private final String orderId;
    private String status;
    private final Client client;
    private final List<Item> items;
    private final Summary summary;
    private final Instant processedAt;

    public void updateStatus(String newStatus) {
        this.status = newStatus;
    }
}
