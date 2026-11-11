package com.corebanking.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Jobs agendados (limpeza de chaves de idempotência, reconciliação). Desligado nos testes. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "bank.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
