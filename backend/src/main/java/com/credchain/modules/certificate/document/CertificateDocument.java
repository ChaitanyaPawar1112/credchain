package com.credchain.modules.certificate.document;

import com.credchain.modules.blockchain.config.ExplorerLinks;
import com.credchain.modules.certificate.crypto.CertificateHasher;
import com.credchain.modules.certificate.domain.BatchStatus;
import com.credchain.modules.certificate.domain.Certificate;
import com.credchain.modules.certificate.domain.CertificateBatch;
import com.credchain.modules.certificate.domain.CertificateStatus;
import com.credchain.modules.certificate.domain.CertificateType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Objects;

/**
 * Everything printed on (or embedded in) one certificate PDF.
 * Built only for certificates whose batch is anchored on-chain, so every blockchain field is known.
 */
public record CertificateDocument(
        String institutionName,
        String certificateNumber,
        CertificateType type,
        String title,
        String program,
        String grade,
        String cgpa,
        LocalDate awardedOn,
        String studentName,
        String enrollmentNo,
        String certHash,
        String canonicalPayload,
        String merkleProof,
        String merkleRoot,
        long chainId,
        String contractAddress,
        String issuerAddress,
        String txHash,
        Instant anchoredAt,
        String verificationUrl,
        String explorerTxUrl
) {

    public static CertificateDocument of(Certificate certificate, CertificateBatch batch, String institutionName,
                                         String contractAddress, String verificationUrl) {
        if (!certificate.getBatchId().equals(batch.getId())) {
            throw new IllegalArgumentException("Certificate does not belong to this batch");
        }
        if (!EnumSet.of(BatchStatus.ANCHORED, BatchStatus.REVOKED).contains(batch.getStatus())
                || certificate.getStatus() == CertificateStatus.DRAFT || certificate.getStatus() == CertificateStatus.PENDING) {
            throw new IllegalStateException("A PDF can only be made for a certificate anchored on-chain");
        }
        return new CertificateDocument(
                Objects.requireNonNull(institutionName, "institutionName"),
                certificate.getCertificateNumber(),
                certificate.getType(),
                certificate.getTitle(),
                certificate.getProgram(),
                certificate.getGrade(),
                CertificateHasher.formatCgpa(certificate.getCgpa()),
                certificate.getAwardedOn(),
                certificate.getStudentName(),
                certificate.getEnrollmentNo(),
                certificate.getCertHash(),
                certificate.getCanonicalPayload(),
                certificate.getMerkleProof(),
                batch.getMerkleRoot(),
                batch.getChainId(),
                Objects.requireNonNull(contractAddress, "contractAddress"),
                batch.getIssuerAddress(),
                batch.getTxHash(),
                batch.getAnchoredAt(),
                Objects.requireNonNull(verificationUrl, "verificationUrl"),
                ExplorerLinks.tx(batch.getChainId(), batch.getTxHash()));
    }
}