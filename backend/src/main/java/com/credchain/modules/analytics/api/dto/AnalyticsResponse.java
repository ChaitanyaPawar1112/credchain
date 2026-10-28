package com.credchain.modules.analytics.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The dashboard. Totals are "right now" (all time); daily, checksByResult, checksByMethod and topColleges cover
 * the chosen period (from..to, both included, in the app's time zone).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AnalyticsResponse(
        LocalDate from,
        LocalDate to,
        Totals totals,
        List<Day> daily,
        Map<String, Long> checksByResult,
        Map<String, Long> checksByMethod,
        /** Super admin only: the most active colleges in the period (null for a college admin). */
        List<College> topColleges
) {

    public record Totals(
            /** Super admin only: approved colleges and applications waiting for review. */
            Long colleges,
            Long pendingApplications,
            long students,
            long studentsWithAccount,
            long certificatesIssued,
            long certificatesRevoked,
            long checksInPeriod,
            long fakeChecksInPeriod
    ) {
    }

    /** One day of the chart. failed = checks answered FAKE or NOT_FOUND. */
    public record Day(LocalDate date, long issued, long revoked, long checks, long valid, long failed) {
    }

    public record College(UUID institutionId, String name, String code, long issued, long checks, long fakeChecks) {
    }
}
