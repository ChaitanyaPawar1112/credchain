package com.credchain.modules.certificate.application;

import com.credchain.modules.certificate.domain.CertificateBatch;
import com.credchain.modules.certificate.infrastructure.CertificateBatchRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.List;

/**
 * Background worker: anchors queued certificate batches, one per transaction
 * (row-locked with SKIP LOCKED so several backend instances never anchor the same batch).
 */
@Component
@ConditionalOnProperty(prefix = "app.blockchain", name = "enabled", havingValue = "true")
public class BatchAnchoringJob {

    private final CertificateBatchRepository batchRepository;
    private final BatchAnchorer anchorer;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final int maxBatchesPerRun;

    public BatchAnchoringJob(CertificateBatchRepository batchRepository,
                             BatchAnchorer anchorer,
                             PlatformTransactionManager transactionManager,
                             Clock clock,

                             @Value("${app.anchoring.max-batches-per-run:3}") int maxBatchesPerRun) {
        this.batchRepository = batchRepository;
        this.anchorer = anchorer;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.maxBatchesPerRun = maxBatchesPerRun;
    }

    @Scheduled(initialDelayString = "${app.anchoring.initial-delay-ms:15000}",
            fixedDelayString = "${app.anchoring.interval-ms:20000}")
    public void run() {
        for (int i = 0; i < maxBatchesPerRun; i++) {
            Boolean handled = transactionTemplate.execute(status -> {
                List<CertificateBatch> due = batchRepository.lockDueForAnchoring(clock.instant(), 1);
                if (due.isEmpty()) {
                    return false;
                }
                anchorer.process(due.get(0));   // changes are saved when the transaction commits
                return true;
            });
            if (!Boolean.TRUE.equals(handled)) {
                return;
            }
        }
    }
}