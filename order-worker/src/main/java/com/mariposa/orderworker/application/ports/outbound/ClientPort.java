package com.mariposa.orderworker.application.ports.outbound;

import com.mariposa.orderworker.domain.model.Client;
import reactor.core.publisher.Mono;

public interface ClientPort {
    Mono<Client> getClientById(String clientId);
}