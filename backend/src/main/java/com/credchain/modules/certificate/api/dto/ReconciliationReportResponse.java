package com.credchain.modules.certificate.api.dto;

import com.credchain.common.api.PageResponse;
import com.credchain.modules.certificate.domain.Certificate;
import com.credchain.modules.certificate.domain.CertificateStatus;

import java.time.Instant;
import java.util.UUID;

/** Super admin view of the blockchain vs database check. */
public record ReconciliationReportResponse(
        long onChainCertificates,
        long neverChecked,
        long mismatches,
        PageResponse<Mismatch> mismatchList
) {

    /** One certificate where the blockchain disagrees with the database. */
    public record Mismatch(
            UUID certificateId,
            UUID institutionId,
            String certificateNumber,
            String studentName,
            CertificateStatus databaseStatus,
            String note,
            Instant checkedAt
    ) {
        public static Mismatch from(Certificate c) {
            return new Mismatch(c.getId(), c.getInstitutionId(), c.getCertificateNumber(), c.getStudentName(),
                    c.getStatus(), c.getChainCheckNote(), c.getChainCheckedAt());
        }
    }
}