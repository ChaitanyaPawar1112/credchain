package com.credchain.modules.certificate.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Background worker: creates the PDF of every certificate that is on-chain but has no PDF yet. */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.storage", name = "enabled", havingValue = "true")
public class CertificatePdfJob {

    private final CertificatePdfService pdfService;
    private final int batchSize;

    public CertificatePdfJob(CertificatePdfService pdfService,
                             @Value("${app.certificate-pdf.batch-size:10}") int batchSize) {
        this.pdfService = pdfService;
        this.batchSize = batchSize;
    }

    @Scheduled(initialDelayString = "${app.certificate-pdf.initial-delay-ms:20000}",
            fixedDelayString = "${app.certificate-pdf.interval-ms:15000}")
    public void run() {
        try {
            int created = pdfService.generateMissing(batchSize);
            if (created > 0) {
                log.info("Certificate PDF worker: {} PDF(s) created", created);
            }
        } catch (RuntimeException e) {
            log.warn("Certificate PDF worker skipped this run: {}", e.getMessage());
        }
    }
}