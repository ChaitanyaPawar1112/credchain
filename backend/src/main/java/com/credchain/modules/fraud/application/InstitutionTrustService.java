package com.credchain.modules.fraud.application;

import com.credchain.modules.audit.application.AuditService;
import com.credchain.modules.audit.domain.AuditAction;
import com.credchain.common.exception.BusinessException;
import com.credchain.common.exception.ErrorCode;
import com.credchain.modules.fraud.api.dto.InstitutionTrustResponse;
import com.credchain.modules.fraud.config.InstitutionTrustProperties;
import com.credchain.modules.fraud.domain.InstitutionTrustScore;
import com.credchain.modules.fraud.domain.TrustLevel;
import com.credchain.modules.fraud.infrastructure.FraudServiceClient;
import com.credchain.modules.fraud.infrastructure.FraudServiceException;
import com.credchain.modules.fraud.infrastructure.InstitutionActivity;
import com.credchain.modules.fraud.infrastructure.InstitutionTrustAnalysis;
import com.credchain.modules.fraud.infrastructure.InstitutionTrustScoreRepository;
import com.credchain.modules.institution.domain.Institution;
import com.credchain.modules.institution.domain.InstitutionStatus;
import com.credchain.modules.institution.infrastructure.InstitutionRepository;
import com.credchain.modules.notification.application.NotificationService;
import com.credchain.modules.notification.domain.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Trust score per college: collects each college's daily activity, asks the fraud-detection service
 * (Isolation Forest + rules) and keeps the latest result. Runs daily and on demand by the super admin.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InstitutionTrustService {

    private final InstitutionActivityQuery activityQuery;
    private final FraudServiceClient fraudService;
    private final InstitutionTrustScoreRepository scoreRepository;
    private final InstitutionRepository institutionRepository;
    private final InstitutionTrustProperties properties;
    private final AuditService audit;
    private final NotificationService notifications;
    private final Clock clock;

    /** The nightly run. */
    @Transactional
    public InstitutionTrustResponse recompute() {
        return recompute(null);
    }

    /**
     * Runs the analysis now and saves the result. 503 FRAUD_SERVICE_UNAVAILABLE when the service is down.
     * requestedBy = the super admin who clicked "Run now" (audited); null for the nightly job.
     */
    @Transactional
    public InstitutionTrustResponse recompute(UUID requestedBy) {
        ZoneId zone = ZoneId.of(properties.timeZone());
        Instant now = clock.instant();
        InstitutionActivity activity = activityQuery.load(LocalDate.ofInstant(now, zone), properties.historyDays(), zone);

        InstitutionTrustAnalysis analysis;
        try {
            analysis = fraudService.analyseInstitutions(activity);
        } catch (FraudServiceException e) {
            throw new BusinessException(ErrorCode.FRAUD_SERVICE_UNAVAILABLE,
                    "The fraud-detection service is not available right now, so trust scores could not be updated.");
        }

        for (InstitutionTrustAnalysis.Trust trust : analysis.institutions()) {
            UUID id = parse(trust.institutionId());
            if (id == null || !institutionRepository.existsById(id)) {
                continue;   // the service only knows the ids we sent; anything else is ignored
            }
            InstitutionTrustScore score = scoreRepository.findById(id).orElseGet(() -> InstitutionTrustScore.of(id));
            TrustLevel before = score.getLevel();
            score.update(trust.trustScore(), level(trust.trustScore()),
                    trust.reasons() == null ? List.of() : trust.reasons(),
                    trust.anomalies() == null ? 0 : trust.anomalies().size(), analysis.modelUsed(), now);
            scoreRepository.save(score);
            if (score.getLevel() == TrustLevel.AT_RISK && before != TrustLevel.AT_RISK) {
                warnAtRisk(id, score);
            }
        }
        log.info("Institution trust analysis: {} colleges, {} days of activity, model used: {}",
                analysis.institutions().size(), activity.days().size(), analysis.modelUsed());
        if (requestedBy != null) {
            audit.record(AuditAction.TRUST_SCORES_RECOMPUTED, requestedBy, null, null, null,
                    "Recalculated the trust scores of " + analysis.institutions().size() + " colleges");
        }
        return list();
    }

    /** Tells the super admins once, when a college newly falls to "At risk". */
    private void warnAtRisk(UUID institutionId, InstitutionTrustScore score) {
        String name = institutionRepository.findById(institutionId).map(Institution::getName).orElse("A college");
        notifications.notifySuperAdmins(NotificationType.COLLEGE_AT_RISK, name + " is now \"At risk\"",
                "Its AI trust score fell to " + score.getTrustScore() + ". "
                        + String.join(" ", score.reasonList()), "/admin/trust");
    }

    /** Every approved or suspended college with its latest score, lowest first; not yet analysed ones last. */
    @Transactional(readOnly = true)
    public InstitutionTrustResponse list() {
        Map<UUID, InstitutionTrustScore> scores = scoreRepository.findAll().stream()
                .collect(Collectors.toMap(InstitutionTrustScore::getInstitutionId, Function.identity()));
        List<InstitutionTrustResponse.Entry> entries = institutionRepository.findAll().stream()
                .filter(i -> EnumSet.of(InstitutionStatus.APPROVED, InstitutionStatus.SUSPENDED).contains(i.getStatus()))
                .map(i -> entry(i, scores.get(i.getId())))
                .sorted(Comparator.comparing((InstitutionTrustResponse.Entry e) -> e.trustScore() == null ? 101 : e.trustScore())
                        .thenComparing(InstitutionTrustResponse.Entry::name))
                .toList();
        Instant last = scores.values().stream().map(InstitutionTrustScore::getComputedAt)
                .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
        return new InstitutionTrustResponse(last, entries);
    }

    /** The level always follows the score, whatever the service said, so the two never disagree. */
    static TrustLevel level(int score) {
        return score >= 80 ? TrustLevel.TRUSTED : score >= 50 ? TrustLevel.WATCH : TrustLevel.AT_RISK;
    }

    private static InstitutionTrustResponse.Entry entry(Institution institution, InstitutionTrustScore score) {
        if (score == null) {
            return new InstitutionTrustResponse.Entry(institution.getId(), institution.getName(), institution.getCode(),
                    null, null, List.of("Not analysed yet."), 0, false, null);
        }
        return new InstitutionTrustResponse.Entry(institution.getId(), institution.getName(), institution.getCode(),
                score.getTrustScore(), score.getLevel(), score.reasonList(), score.getAnomalyCount(), score.isModelUsed(),
                score.getComputedAt());
    }

    private static UUID parse(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }
}
