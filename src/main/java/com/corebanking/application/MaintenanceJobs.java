package com.corebanking.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "bank.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class MaintenanceJobs {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceJobs.class);

    private final IdempotencyService idempotency;
    private final ReconciliationService reconciliation;

    public MaintenanceJobs(IdempotencyService idempotency, ReconciliationService reconciliation) {
        this.idempotency = idempotency;
        this.reconciliation = reconciliation;
    }

    @Scheduled(fixedDelayString = "${bank.idempotency.cleanup-interval:PT1H}", initialDelayString = "PT5M")
    public void purgeIdempotencyKeys() {
        int removed = idempotency.purgeExpired();
        if (removed > 0) {
            log.info("Chaves de idempotência expiradas removidas: {}", removed);
        }
    }

    @Scheduled(cron = "${bank.reconciliation.cron:0 0 3 * * *}")
    public void reconcile() {
        var issues = reconciliation.findInconsistencies();
        if (issues.isEmpty()) {
            log.info("Reconciliação OK: saldos consistentes com o ledger");
        } else {
            issues.forEach(i -> log.error("DIVERGÊNCIA conta={} saldo={} ledger={}",
                    i.accountId(), i.storedBalance(), i.ledgerBalance()));
        }
    }
}
