package com.mariposa.orderworker.infrastructure.dto;

import com.mariposa.orderworker.domain.model.Client;

public record ClientResponseDto(
        String clientId,
        String name,
        String segment,
        String taxRegime,
        String region,
        String channel
) {
    public Client toDomain() {
        return Client.builder()
                .clientId(this.clientId)
                .name(this.name)
                .segment(this.segment)
                .taxRegime(this.taxRegime)
                .region(this.region)
                .channel(this.channel)
                .build();
    }
}