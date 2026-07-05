package com.mariposa.orderworker.infrastructure.config;

import com.mariposa.orderworker.domain.service.OrderDomainService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainConfig {
    @Bean
    public OrderDomainService orderDomainService() {
        return new OrderDomainService();
    }
}