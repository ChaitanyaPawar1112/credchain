package com.credchain.modules.verification.application;

import com.credchain.common.api.PageResponse;
import com.credchain.modules.certificate.domain.Certificate;
import com.credchain.modules.certificate.infrastructure.CertificateRepository;
import com.credchain.modules.institution.application.InstitutionAccessGuard;
import com.credchain.modules.verification.api.dto.PdfVerificationResponse;
import com.credchain.modules.verification.api.dto.VerificationActivityResponse;
import com.credchain.modules.verification.api.dto.VerificationResponse;
import com.credchain.modules.verification.domain.VerificationLog;
import com.credchain.modules.verification.domain.VerificationMethod;
import com.credchain.modules.verification.domain.VerificationStatus;
import com.credchain.modules.verification.infrastructure.VerificationLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Records every public check and shows them to institution admins (their own certificates)
 * and the super admin (everything, e.g. all FAKE attempts).
 */
@Slf4j
@Service
public class VerificationLogService {

    private final VerificationLogRepository logRepository;
    private final CertificateRepository certificateRepository;
    private final InstitutionAccessGuard accessGuard;
    private final TransactionTemplate newTransaction;

    public VerificationLogService(VerificationLogRepository logRepository, CertificateRepository certificateRepository,
                                  InstitutionAccessGuard accessGuard, PlatformTransactionManager transactionManager) {
        this.logRepository = logRepository;
        this.certificateRepository = certificateRepository;
        this.accessGuard = accessGuard;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // ---------- recording ----------

    public void recordHashCheck(VerificationResponse response, String userAgent) {
        record(response.checkedAt(), VerificationMethod.HASH, response.status(), response.blockchainChecked(),
                response.certHash(), response.status() != VerificationStatus.NOT_FOUND ? response : null, userAgent);
    }

    public void recordPdfCheck(PdfVerificationResponse response, String userAgent) {
        VerificationResponse record = response.record();
        record(response.checkedAt(), VerificationMethod.PDF, response.status(), record != null && record.blockchainChecked(),
                record == null ? null : record.certHash(), record, userAgent);
    }

    /**
     * linked = the official record the check was about, or null.
     * Saved in its own transaction; a failure is only logged, so the verifier still gets the answer.
     * The certificate is linked only when the check was about a real issued certificate
     * (never for NOT_FOUND, so draft certificates stay hidden).
     */
    private void record(Instant checkedAt, VerificationMethod method, VerificationStatus result,
                        boolean blockchainChecked, String certHash, VerificationResponse linked, String userAgent) {
        try {
            newTransaction.executeWithoutResult(status -> {
                Optional<Certificate> certificate = linked == null
                        ? Optional.empty()
                        : certificateRepository.findByCertHash(linked.certHash());
                logRepository.save(VerificationLog.of(checkedAt, method, result, blockchainChecked, certHash,
                        certificate.map(Certificate::getId).orElse(null),
                        certificate.map(Certificate::getInstitutionId).orElse(null),
                        userAgent));
            });
        } catch (RuntimeException e) {
            log.warn("Could not save verification log ({} {}): {}", method, result, e.getMessage());
        }
    }

    // ---------- reading ----------

    /** Institution admin: checks of their own institution's certificates. */
    @Transactional(readOnly = true)
    public VerificationActivityResponse forInstitution(UUID adminUserId, VerificationStatus result, int page, int size) {
        UUID institutionId = accessGuard.requireActiveInstitution(adminUserId).getId();
        PageRequest pageRequest = newestFirst(page, size);
        Page<VerificationLog> logs = result == null
                ? logRepository.findAllByInstitutionId(institutionId, pageRequest)
                : logRepository.findAllByInstitutionIdAndResult(institutionId, result, pageRequest);
        return response(logRepository.countByResultForInstitution(institutionId), logs);
    }

    /** Super admin: every check on the platform, optionally only one result (e.g. FAKE). */
    @Transactional(readOnly = true)
    public VerificationActivityResponse forPlatform(VerificationStatus result, int page, int size) {
        PageRequest pageRequest = newestFirst(page, size);
        Page<VerificationLog> logs = result == null
                ? logRepository.findAll(pageRequest)
                : logRepository.findAllByResult(result, pageRequest);
        return response(logRepository.countByResult(), logs);
    }

    private VerificationActivityResponse response(List<Object[]> counts, Page<VerificationLog> logs) {
        Map<VerificationStatus, Long> byResult = new EnumMap<>(VerificationStatus.class);
        for (VerificationStatus status : VerificationStatus.values()) {
            byResult.put(status, 0L);
        }
        long total = 0;
        for (Object[] row : counts) {
            long count = ((Number) row[1]).longValue();
            byResult.put((VerificationStatus) row[0], count);
            total += count;
        }

        List<UUID> certificateIds = logs.getContent().stream()
                .map(VerificationLog::getCertificateId).filter(id -> id != null).distinct().toList();
        Map<UUID, Certificate> certificates = certificateRepository.findAllById(certificateIds).stream()
                .collect(Collectors.toMap(Certificate::getId, Function.identity()));

        return new VerificationActivityResponse(total, byResult, PageResponse.from(logs.map(l -> {
            Certificate c = l.getCertificateId() == null ? null : certificates.get(l.getCertificateId());
            return new VerificationActivityResponse.Entry(l.getId(), l.getCheckedAt(), l.getMethod(), l.getResult(),
                    l.isBlockchainChecked(), l.getCertHash(), l.getCertificateId(),
                    c == null ? null : c.getCertificateNumber(), c == null ? null : c.getStudentName(),
                    l.getInstitutionId(), l.getUserAgent());
        })));
    }

    private static PageRequest newestFirst(int page, int size) {
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "checkedAt"));
    }
}
