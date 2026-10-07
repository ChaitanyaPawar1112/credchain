package com.credchain.modules.certificate.application;

import com.credchain.common.api.PageResponse;
import com.credchain.common.exception.BusinessException;
import com.credchain.common.exception.ErrorCode;
import com.credchain.modules.certificate.api.dto.CertificateResponse;
import com.credchain.modules.certificate.api.dto.RevokeCertificateRequest;
import com.credchain.modules.certificate.domain.Certificate;
import com.credchain.modules.certificate.domain.CertificateStatus;
import com.credchain.modules.certificate.infrastructure.CertificateRepository;
import com.credchain.modules.institution.application.InstitutionAccessGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Locale;
import java.util.UUID;

/** Institution admin: view issued certificates and request revocation (executed on-chain by the worker). */
@Slf4j
@Service
@RequiredArgsConstructor
public class CertificateRevocationService {

    private final InstitutionAccessGuard accessGuard;
    private final CertificateRepository certificateRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<CertificateResponse> list(UUID adminUserId, CertificateStatus status, String search,
                                                  int page, int size) {
        UUID institutionId = accessGuard.requireActiveInstitution(adminUserId).getId();
        String pattern = (search == null || search.isBlank())
                ? "%"
                : "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.from(certificateRepository.searchInInstitution(institutionId, status, pattern, pageRequest)
                .map(CertificateResponse::from));
    }

    @Transactional(readOnly = true)
    public CertificateResponse get(UUID adminUserId, UUID certificateId) {
        return CertificateResponse.from(load(adminUserId, certificateId));
    }

    @Transactional
    public CertificateResponse requestRevocation(UUID adminUserId, UUID certificateId, RevokeCertificateRequest request) {
        Certificate certificate = load(adminUserId, certificateId);
        certificate.requestRevocation(request.reason(), request.note(), clock.instant());   // 409 unless ISSUED
        log.warn("Revocation of certificate {} ({}) requested by {}: {}",
                certificate.getCertificateNumber(), certificate.getId(), adminUserId, request.reason());
        return CertificateResponse.from(certificate);
    }

    private Certificate load(UUID adminUserId, UUID certificateId) {
        UUID institutionId = accessGuard.requireActiveInstitution(adminUserId).getId();
        return certificateRepository.findByIdAndInstitutionId(certificateId, institutionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Certificate not found"));
    }
}