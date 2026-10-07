package com.credchain.modules.certificate.application;

import com.credchain.modules.blockchain.infrastructure.BlockchainException;
import com.credchain.modules.certificate.domain.Certificate;
import com.credchain.modules.certificate.domain.ChainCheckResult;
import com.credchain.modules.certificate.infrastructure.CertificateRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * Background worker: re-reads on-chain certificates from the smart contract and compares them with the database.
 * Never-checked certificates go first, then the ones checked longest ago. Each certificate is re-checked at most
 * once per recheck interval, so the job costs only a few free view calls per run.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.blockchain", name = "enabled", havingValue = "true")
public class ChainReconciliationJob {

    private final CertificateRepository certificateRepository;
    private final ChainReconciler reconciler;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final int batchSize;
    private final Duration recheckAfter;

    public ChainReconciliationJob(CertificateRepository certificateRepository,
                                  ChainReconciler reconciler,
                                  PlatformTransactionManager transactionManager,
                                  Clock clock,
                                  @Value("${app.reconciliation.batch-size:25}") int batchSize,
                                  @Value("${app.reconciliation.recheck-after-hours:6}") long recheckAfterHours) {
        this.certificateRepository = certificateRepository;
        this.reconciler = reconciler;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.batchSize = batchSize;
        this.recheckAfter = Duration.ofHours(recheckAfterHours);
    }

    @Scheduled(initialDelayString = "${app.reconciliation.initial-delay-ms:60000}",
            fixedDelayString = "${app.reconciliation.interval-ms:300000}")
    public void run() {
        try {
            int[] counts = transactionTemplate.execute(status -> checkDue());
            if (counts != null && counts[0] > 0) {
                log.info("Reconciliation: {} certificate(s) checked against the blockchain, {} mismatch(es)",
                        counts[0], counts[1]);
            }
        } catch (RuntimeException e) {
            log.warn("Reconciliation skipped this run: {}", e.getMessage());
        }
    }

    /** @return {checked, mismatches} */
    private int[] checkDue() {
        List<Certificate> due = certificateRepository.lockDueForChainCheck(clock.instant().minus(recheckAfter), batchSize);
        int checked = 0;
        int mismatches = 0;
        for (Certificate certificate : due) {
            try {
                if (reconciler.check(certificate) == ChainCheckResult.MISMATCH) {
                    mismatches++;
                }
                checked++;
            } catch (BlockchainException e) {
                // RPC problem: keep what was checked so far, try the rest next run
                log.warn("Reconciliation stopped early, blockchain not reachable: {}", e.getMessage());
                break;
            }
        }
        return new int[] {checked, mismatches};
    }
}