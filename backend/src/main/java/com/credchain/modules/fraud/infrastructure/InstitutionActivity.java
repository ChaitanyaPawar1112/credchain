package com.credchain.modules.fraud.infrastructure;

import java.time.LocalDate;
import java.util.List;

/** Daily activity of every college, sent to the fraud-detection service for the trust analysis. */
public record InstitutionActivity(LocalDate today, List<Institution> institutions, List<Day> days) {

    public record Institution(String institutionId, String name) {
    }

    /** One college on one day (in the platform's time zone). */
    public record Day(
            String institutionId,
            LocalDate date,
            int issued,
            int revoked,
            int checks,
            /** Public PDF / hash checks that came back FAKE. */
            int fakeChecks,
            /** Photo / scan checks with a HIGH risk score. */
            int highRiskChecks,
            /** Certificates queued for issuing between midnight and 6 a.m. */
            int offHoursIssued
    ) {
    }
}
