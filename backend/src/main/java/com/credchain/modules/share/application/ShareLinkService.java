package com.credchain.modules.share.application;

import com.credchain.common.api.PageResponse;
import com.credchain.common.exception.BusinessException;
import com.credchain.common.exception.ErrorCode;
import com.credchain.modules.audit.application.AuditService;
import com.credchain.modules.audit.domain.AuditAction;
import com.credchain.modules.certificate.application.CertificatePdfService;
import com.credchain.modules.certificate.domain.Certificate;
import com.credchain.modules.certificate.domain.CertificateStatus;
import com.credchain.modules.certificate.infrastructure.CertificateRepository;
import com.credchain.modules.share.api.dto.CreateShareLinkRequest;
import com.credchain.modules.share.api.dto.ShareLinkResponse;
import com.credchain.modules.share.api.dto.SharedCertificateResponse;
import com.credchain.modules.share.config.ShareLinkProperties;
import com.credchain.modules.share.domain.ShareLink;
import com.credchain.modules.share.infrastructure.ShareLinkRepository;
import com.credchain.modules.student.domain.Student;
import com.credchain.modules.student.infrastructure.StudentRepository;
import com.credchain.modules.verification.api.dto.VerificationResponse;
import com.credchain.modules.verification.application.PublicVerificationService;
import com.credchain.modules.verification.application.VerificationLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Students create, list and turn off their links; anyone with an active link sees the certificate's live result. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShareLinkService {

    private final ShareLinkRepository repository;
    private final StudentRepository studentRepository;
    private final CertificateRepository certificateRepository;
    private final CertificatePdfService pdfService;
    private final PublicVerificationService verificationService;
    private final VerificationLogService logService;
    private final ShareLinkProperties properties;
    private final AuditService audit;
    private final Clock clock;

    // ---------- the student's side ----------

    /** Only the student's own issued (not revoked) certificates can be shared. */
    @Transactional
    public ShareLinkResponse create(UUID userId, CreateShareLinkRequest request) {
        Student student = myStudentRecord(userId);
        Certificate certificate = certificateRepository
                .findByIdAndStudentIdAndStatusIn(request.certificateId(), student.getId(), CertificateStatus.ON_CHAIN)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Certificate not found"));
        if (certificate.getStatus() != CertificateStatus.ISSUED) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "A revoked certificate cannot be shared.");
        }
        Instant now = clock.instant();
        if (repository.countActive(student.getId(), now) >= properties.maxActivePerStudent()) {
            throw new BusinessException(ErrorCode.SHARE_LINK_LIMIT_REACHED);
        }
        ShareLink link = repository.save(ShareLink.create(certificate.getId(), student.getId(), request.name(), now,
                now.plus(Duration.ofDays(request.expiresInDays()))));
        // the label stays private: it is not written to the audit log the college can read
        audit.record(AuditAction.SHARE_LINK_CREATED, userId, null, "SHARE_LINK", link.getId(),
                "Created a share link to certificate " + certificate.getCertificateNumber()
                        + " valid for " + request.expiresInDays() + " days");
        return toResponse(link, certificate, now);
    }

    @Transactional(readOnly = true)
    public PageResponse<ShareLinkResponse> list(UUID userId, int page, int size) {
        Student student = myStudentRecord(userId);
        Page<ShareLink> links = repository.findAllByStudentId(student.getId(),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        Map<UUID, Certificate> certificates = certificateRepository
                .findAllById(links.map(ShareLink::getCertificateId).toSet()).stream()
                .collect(Collectors.toMap(Certificate::getId, Function.identity()));
        Instant now = clock.instant();
        return PageResponse.from(links.map(l -> toResponse(l, certificates.get(l.getCertificateId()), now)));
    }

    /** Turns a link off for good; anyone opening it afterwards is told it no longer works. */
    @Transactional
    public ShareLinkResponse revoke(UUID userId, UUID linkId) {
        Student student = myStudentRecord(userId);
        ShareLink link = repository.findByIdAndStudentId(linkId, student.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Share link not found"));
        Instant now = clock.instant();
        if (link.getRevokedAt() == null) {
            link.revoke(now);
            audit.record(AuditAction.SHARE_LINK_REVOKED, userId, null, "SHARE_LINK", link.getId(),
                    "Turned off a share link (opened " + link.getViewCount() + " times)");
        }
        return toResponse(link, certificateRepository.findById(link.getCertificateId()).orElse(null), now);
    }

    // ---------- anyone with the link ----------

    /**
     * Counts the view and answers with the live verification result. An unknown, expired or turned-off link
     * gets one and the same 404, so a guessed token reveals nothing.
     */
    @Transactional
    public SharedCertificateResponse open(String token, String userAgent) {
        Instant now = clock.instant();
        ShareLink link = activeLink(token, now, true);
        Certificate certificate = certificateRepository.findById(link.getCertificateId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SHARE_LINK_UNAVAILABLE));
        link.recordView(now);
        VerificationResponse verification = verificationService.verifyByHash(certificate.getCertHash());
        logService.recordHashCheck(verification, userAgent);
        return new SharedCertificateResponse(verification, link.getExpiresAt(), certificate.hasPdf());
    }

    /** The certificate's PDF through an active link (not counted again as a view). */
    @Transactional(readOnly = true)
    public CertificatePdfService.PdfFile pdf(String token) {
        ShareLink link = activeLink(token, clock.instant(), false);
        Certificate certificate = certificateRepository.findById(link.getCertificateId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SHARE_LINK_UNAVAILABLE));
        return pdfService.read(certificate);
    }

    // ---------- helpers ----------

    private ShareLink activeLink(String token, Instant now, boolean lock) {
        if (token == null || token.length() != 43) {
            throw new BusinessException(ErrorCode.SHARE_LINK_UNAVAILABLE);
        }
        return (lock ? repository.lockByToken(token) : repository.findByToken(token))
                .filter(l -> l.isActive(now))
                .orElseThrow(() -> new BusinessException(ErrorCode.SHARE_LINK_UNAVAILABLE));
    }

    private Student myStudentRecord(UUID userId) {
        return studentRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Your account is not linked to a student record yet. Ask your institution for a claim code."));
    }

    private ShareLinkResponse toResponse(ShareLink l, Certificate c, Instant now) {
        return new ShareLinkResponse(l.getId(), l.getName(), properties.urlFor(l.getToken()), l.getCertificateId(),
                c == null ? null : c.getCertificateNumber(), c == null ? null : c.getTitle(), l.status(now),
                l.getCreatedAt(), l.getExpiresAt(), l.getRevokedAt(), l.getViewCount(), l.getLastViewedAt());
    }
}
