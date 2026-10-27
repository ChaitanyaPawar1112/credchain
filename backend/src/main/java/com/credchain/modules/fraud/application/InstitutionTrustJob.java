package com.credchain.modules.fraud.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Background worker: refreshes every college's trust score once a day (default 02:30 in app.fraud.trust.time-zone). */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.fraud", name = "enabled", havingValue = "true")
public class InstitutionTrustJob {

    private final InstitutionTrustService trustService;

    @Scheduled(cron = "${app.fraud.trust.cron}", zone = "${app.fraud.trust.time-zone}")
    public void run() {
        try {
            trustService.recompute();
        } catch (RuntimeException e) {   // service down: keep yesterday's scores, try again tomorrow
            log.warn("Institution trust analysis skipped this run: {}", e.getMessage());
        }
    }
}
