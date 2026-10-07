package com.credchain.modules.certificate.application;

import com.credchain.common.api.PageResponse;
import com.credchain.modules.certificate.api.dto.ReconciliationReportResponse;
import com.credchain.modules.certificate.domain.CertificateStatus;
import com.credchain.modules.certificate.domain.ChainCheckResult;
import com.credchain.modules.certificate.infrastructure.CertificateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Super admin: summary of the reconciliation job and the list of mismatches. */
@Service
@RequiredArgsConstructor
public class ChainReconciliationService {

    private final CertificateRepository certificateRepository;

    @Transactional(readOnly = true)
    public ReconciliationReportResponse report(int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "chainCheckedAt"));
        return new ReconciliationReportResponse(
                certificateRepository.countByStatusIn(CertificateStatus.ON_CHAIN),
                certificateRepository.countByStatusInAndChainCheckedAtIsNull(CertificateStatus.ON_CHAIN),
                certificateRepository.countByChainCheckResult(ChainCheckResult.MISMATCH),
                PageResponse.from(certificateRepository.findAllByChainCheckResult(ChainCheckResult.MISMATCH, pageRequest)
                        .map(ReconciliationReportResponse.Mismatch::from)));
    }
}