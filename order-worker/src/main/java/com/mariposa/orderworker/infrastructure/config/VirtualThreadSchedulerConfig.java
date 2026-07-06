package com.mariposa.orderworker.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import java.util.concurrent.Executors;

@Configuration
public class VirtualThreadSchedulerConfig {
    @Bean(name = "virtualThreadScheduler")
    public Scheduler virtualThreadScheduler() {
        return Schedulers.fromExecutorService(
                Executors.newVirtualThreadPerTaskExecutor(),
                "order-loom-pool"
        );
    }
}