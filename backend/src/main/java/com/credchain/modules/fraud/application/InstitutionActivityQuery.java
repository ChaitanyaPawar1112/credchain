package com.credchain.modules.fraud.application;

import com.credchain.modules.fraud.infrastructure.InstitutionActivity;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Counts what every approved (or suspended) college did each day: certificates issued and revoked, public checks
 * of its certificates, forged copies seen. Quiet days are included as zeros: the model needs them to learn "normal".
 */
@Component
@RequiredArgsConstructor
class InstitutionActivityQuery {

    private final NamedParameterJdbcTemplate jdbc;

    InstitutionActivity load(LocalDate today, int historyDays, ZoneId zone) {
        LocalDate from = today.minusDays(historyDays - 1L);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tz", zone.getId())
                .addValue("from", Timestamp.from(from.atStartOfDay(zone).toInstant()));

        List<InstitutionActivity.Institution> institutions = new ArrayList<>();
        Map<String, LocalDate> firstDay = new HashMap<>();
        jdbc.query("""
                SELECT id, name, (created_at AT TIME ZONE :tz)::date AS first_day
                FROM institutions
                WHERE status IN ('APPROVED', 'SUSPENDED')
                ORDER BY name
                """, params, rs -> {
            String id = rs.getString("id");
            institutions.add(new InstitutionActivity.Institution(id, rs.getString("name")));
            LocalDate created = rs.getObject("first_day", LocalDate.class);
            firstDay.put(id, created.isAfter(from) ? created : from);
        });

        Map<String, Counts> counts = new HashMap<>();
        jdbc.query("""
                SELECT institution_id, (anchored_at AT TIME ZONE :tz)::date AS day,
                       SUM(certificate_count) AS issued,
                       SUM(CASE WHEN EXTRACT(HOUR FROM COALESCE(queued_at, anchored_at) AT TIME ZONE :tz) < 6
                                THEN certificate_count ELSE 0 END) AS off_hours
                FROM certificate_batches
                WHERE anchored_at >= :from
                GROUP BY 1, 2
                """, params, rs -> {
            Counts c = counts.computeIfAbsent(key(rs.getString(1), rs.getObject(2, LocalDate.class)), k -> new Counts());
            c.issued += rs.getInt("issued");
            c.offHoursIssued += rs.getInt("off_hours");
        });
        jdbc.query("""
                SELECT institution_id, (COALESCE(revoked_at, updated_at) AT TIME ZONE :tz)::date AS day, COUNT(*)
                FROM certificates
                WHERE status IN ('REVOCATION_PENDING', 'REVOKED') AND COALESCE(revoked_at, updated_at) >= :from
                GROUP BY 1, 2
                """, params, rs -> {
            counts.computeIfAbsent(key(rs.getString(1), rs.getObject(2, LocalDate.class)), k -> new Counts())
                    .revoked += rs.getInt(3);
        });
        jdbc.query("""
                SELECT institution_id, (checked_at AT TIME ZONE :tz)::date AS day, COUNT(*) AS checks,
                       COUNT(*) FILTER (WHERE result = 'FAKE' AND method <> 'DOCUMENT') AS fakes,
                       COUNT(*) FILTER (WHERE method = 'DOCUMENT' AND risk_score >= 55) AS high_risk
                FROM verification_logs
                WHERE institution_id IS NOT NULL AND checked_at >= :from
                GROUP BY 1, 2
                """, params, rs -> {
            Counts c = counts.computeIfAbsent(key(rs.getString(1), rs.getObject(2, LocalDate.class)), k -> new Counts());
            c.checks += rs.getInt("checks");
            c.fakeChecks += rs.getInt("fakes");
            c.highRiskChecks += rs.getInt("high_risk");
        });

        List<InstitutionActivity.Day> days = new ArrayList<>();
        for (InstitutionActivity.Institution institution : institutions) {
            for (LocalDate day = firstDay.get(institution.institutionId()); !day.isAfter(today); day = day.plusDays(1)) {
                Counts c = counts.getOrDefault(key(institution.institutionId(), day), Counts.NONE);
                days.add(new InstitutionActivity.Day(institution.institutionId(), day, c.issued, c.revoked, c.checks,
                        c.fakeChecks, c.highRiskChecks, c.offHoursIssued));
            }
        }
        return new InstitutionActivity(today, institutions, days);
    }

    private static String key(String institutionId, LocalDate day) {
        return institutionId + "|" + day;
    }

    private static final class Counts {
        static final Counts NONE = new Counts();
        int issued;
        int revoked;
        int checks;
        int fakeChecks;
        int highRiskChecks;
        int offHoursIssued;
    }
}
