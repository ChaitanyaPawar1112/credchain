package com.credchain.modules.analytics.application;

import com.credchain.modules.analytics.api.dto.AnalyticsResponse;
import com.credchain.modules.analytics.api.dto.AnalyticsResponse.College;
import com.credchain.modules.analytics.api.dto.AnalyticsResponse.Day;
import com.credchain.modules.analytics.api.dto.AnalyticsResponse.Totals;
import com.credchain.modules.institution.application.InstitutionAccessGuard;
import com.credchain.modules.verification.domain.VerificationMethod;
import com.credchain.modules.verification.domain.VerificationStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds the dashboard with a few grouped SQL queries. Every query takes an optional institution id:
 * null = the whole platform (super admin), otherwise one college.
 */
@Service
public class AnalyticsService {

    /** "No college filter" in the SQL below: CAST(:institutionId AS uuid) IS NULL OR institution_id = :institutionId. */
    private static final String SCOPE = "(CAST(:institutionId AS uuid) IS NULL OR institution_id = CAST(:institutionId AS uuid))";

    private final NamedParameterJdbcTemplate jdbc;
    private final InstitutionAccessGuard accessGuard;
    private final Clock clock;
    private final ZoneId zone;

    public AnalyticsService(NamedParameterJdbcTemplate jdbc, InstitutionAccessGuard accessGuard, Clock clock,
                            @Value("${app.analytics.time-zone}") String timeZone) {
        this.jdbc = jdbc;
        this.accessGuard = accessGuard;
        this.clock = clock;
        this.zone = ZoneId.of(timeZone);
    }

    /** Super admin: the whole platform. */
    @Transactional(readOnly = true)
    public AnalyticsResponse forPlatform(int days) {
        return build(null, days);
    }

    /** College admin: their own college only. */
    @Transactional(readOnly = true)
    public AnalyticsResponse forInstitution(UUID adminUserId, int days) {
        return build(accessGuard.requireActiveInstitution(adminUserId).getId(), days);
    }

    private AnalyticsResponse build(UUID institutionId, int days) {
        LocalDate to = LocalDate.now(clock.withZone(zone));
        LocalDate from = to.minusDays(days - 1L);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("institutionId", institutionId == null ? null : institutionId.toString())
                .addValue("tz", zone.getId())
                .addValue("from", Timestamp.from(from.atStartOfDay(zone).toInstant()));

        Map<LocalDate, long[]> perDay = new LinkedHashMap<>();   // issued, revoked, checks, valid, failed
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            perDay.put(d, new long[5]);
        }
        jdbc.query("""
                SELECT (anchored_at AT TIME ZONE :tz)::date AS day, SUM(certificate_count)
                FROM certificate_batches
                WHERE anchored_at >= :from AND %s
                GROUP BY 1
                """.formatted(SCOPE), params, rs -> {
            add(perDay, rs.getObject(1, LocalDate.class), 0, rs.getLong(2));
        });
        jdbc.query("""
                SELECT (COALESCE(revoked_at, updated_at) AT TIME ZONE :tz)::date AS day, COUNT(*)
                FROM certificates
                WHERE status IN ('REVOCATION_PENDING', 'REVOKED') AND COALESCE(revoked_at, updated_at) >= :from AND %s
                GROUP BY 1
                """.formatted(SCOPE), params, rs -> {
            add(perDay, rs.getObject(1, LocalDate.class), 1, rs.getLong(2));
        });

        Map<String, Long> byResult = zeros(VerificationStatus.values());
        Map<String, Long> byMethod = zeros(VerificationMethod.values());
        jdbc.query("""
                SELECT (checked_at AT TIME ZONE :tz)::date AS day, result, method, COUNT(*)
                FROM verification_logs
                WHERE checked_at >= :from AND %s
                GROUP BY 1, 2, 3
                """.formatted(SCOPE), params, rs -> {
            LocalDate day = rs.getObject(1, LocalDate.class);
            String result = rs.getString(2);
            long count = rs.getLong(4);
            add(perDay, day, 2, count);
            if (VerificationStatus.VALID.name().equals(result)) {
                add(perDay, day, 3, count);
            } else if (VerificationStatus.FAKE.name().equals(result) || VerificationStatus.NOT_FOUND.name().equals(result)) {
                add(perDay, day, 4, count);
            }
            byResult.merge(result, count, Long::sum);
            byMethod.merge(rs.getString(3), count, Long::sum);
        });

        List<Day> daily = new ArrayList<>();
        perDay.forEach((d, c) -> daily.add(new Day(d, c[0], c[1], c[2], c[3], c[4])));

        long checks = byResult.values().stream().mapToLong(Long::longValue).sum();
        Totals totals = totals(params, institutionId == null, checks, byResult.get(VerificationStatus.FAKE.name()));
        return new AnalyticsResponse(from, to, totals, daily, byResult, byMethod,
                institutionId == null ? topColleges(params) : null);
    }

    private Totals totals(MapSqlParameterSource params, boolean platform, long checks, long fakes) {
        long[] students = new long[2];
        jdbc.query("SELECT COUNT(*), COUNT(user_id) FROM students WHERE " + SCOPE, params, rs -> {
            students[0] = rs.getLong(1);
            students[1] = rs.getLong(2);
        });
        Map<String, Long> certificates = new HashMap<>();
        jdbc.query("""
                SELECT status, COUNT(*) FROM certificates
                WHERE status IN ('ISSUED', 'REVOCATION_PENDING', 'REVOKED') AND %s
                GROUP BY status
                """.formatted(SCOPE), params, rs -> {
            certificates.put(rs.getString(1), rs.getLong(2));
        });
        Long colleges = null;
        Long pending = null;
        if (platform) {
            Map<String, Long> byStatus = new HashMap<>();
            jdbc.query("SELECT status, COUNT(*) FROM institutions GROUP BY status", rs -> {
                byStatus.put(rs.getString(1), rs.getLong(2));
            });
            colleges = byStatus.getOrDefault("APPROVED", 0L);
            pending = byStatus.getOrDefault("PENDING", 0L);
        }
        return new Totals(colleges, pending, students[0], students[1],
                certificates.getOrDefault("ISSUED", 0L),
                certificates.getOrDefault("REVOCATION_PENDING", 0L) + certificates.getOrDefault("REVOKED", 0L),
                checks, fakes);
    }

    /** The 5 colleges that issued the most in the period (then by checks). */
    private List<College> topColleges(MapSqlParameterSource params) {
        return jdbc.query("""
                SELECT i.id, i.name, i.code,
                       COALESCE(b.issued, 0) AS issued, COALESCE(v.checks, 0) AS checks, COALESCE(v.fakes, 0) AS fakes
                FROM institutions i
                LEFT JOIN (SELECT institution_id, SUM(certificate_count) AS issued
                           FROM certificate_batches WHERE anchored_at >= :from GROUP BY 1) b ON b.institution_id = i.id
                LEFT JOIN (SELECT institution_id, COUNT(*) AS checks, COUNT(*) FILTER (WHERE result = 'FAKE') AS fakes
                           FROM verification_logs WHERE checked_at >= :from AND institution_id IS NOT NULL
                           GROUP BY 1) v ON v.institution_id = i.id
                WHERE i.status IN ('APPROVED', 'SUSPENDED') AND (b.issued IS NOT NULL OR v.checks IS NOT NULL)
                ORDER BY issued DESC, checks DESC, i.name
                LIMIT 5
                """, params, (rs, n) -> new College(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                rs.getLong(4), rs.getLong(5), rs.getLong(6)));
    }

    private static void add(Map<LocalDate, long[]> perDay, LocalDate day, int index, long value) {
        long[] counts = perDay.get(day);
        if (counts != null) {   // a row from just after midnight of "tomorrow" in another clock is ignored
            counts[index] += value;
        }
    }

    private static Map<String, Long> zeros(Enum<?>[] values) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (Enum<?> value : values) {
            map.put(value.name(), 0L);
        }
        return map;
    }
}
