package com.credchain.modules.certificate.application;

import com.credchain.modules.certificate.domain.Certificate;
import com.credchain.modules.certificate.infrastructure.CertificateRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.List;

/** Background worker: executes requested revocations on-chain, one certificate per transaction. */
@Component
@ConditionalOnProperty(prefix = "app.blockchain", name = "enabled", havingValue = "true")
public class CertificateRevocationJob {

    private final CertificateRepository certificateRepository;
    private final CertificateRevoker revoker;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final int maxPerRun;

    public CertificateRevocationJob(CertificateRepository certificateRepository,
                                    CertificateRevoker revoker,
                                    PlatformTransactionManager transactionManager,
                                    Clock clock,
                                    @Value("${app.revocation.max-per-run:5}") int maxPerRun) {
        this.certificateRepository = certificateRepository;
        this.revoker = revoker;

        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.maxPerRun = maxPerRun;
    }

    @Scheduled(initialDelayString = "${app.revocation.initial-delay-ms:20000}",
            fixedDelayString = "${app.revocation.interval-ms:20000}")
    public void run() {
        for (int i = 0; i < maxPerRun; i++) {
            Boolean handled = transactionTemplate.execute(status -> {
                List<Certificate> due = certificateRepository.lockDueForRevocation(clock.instant(), 1);
                if (due.isEmpty()) {
                    return false;
                }
                revoker.process(due.get(0));
                return true;
            });
            if (!Boolean.TRUE.equals(handled)) {
                return;
            }
        }
    }
}