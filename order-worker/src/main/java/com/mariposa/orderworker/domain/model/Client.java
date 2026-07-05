package com.mariposa.orderworker.domain.model;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
public class Client {
    private final String clientId;
    private final String name;
    private final String segment;
    private final String taxRegime;
    private final String region;
}
