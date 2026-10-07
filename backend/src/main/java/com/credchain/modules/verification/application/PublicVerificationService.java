package com.credchain.modules.verification.application;

import com.credchain.common.exception.BusinessException;
import com.credchain.common.exception.ErrorCode;
import com.credchain.modules.blockchain.config.BlockchainProperties;
import com.credchain.modules.blockchain.config.ExplorerLinks;
import com.credchain.modules.blockchain.contract.CredentialRegistryClient;
import com.credchain.modules.blockchain.infrastructure.BlockchainException;
import com.credchain.modules.certificate.crypto.CertificateHasher;
import com.credchain.modules.certificate.crypto.MerkleProofJson;
import com.credchain.modules.certificate.domain.Certificate;
import com.credchain.modules.certificate.domain.CertificateBatch;
import com.credchain.modules.certificate.domain.CertificateStatus;
import com.credchain.modules.certificate.infrastructure.CertificateBatchRepository;
import com.credchain.modules.certificate.infrastructure.CertificateRepository;
import com.credchain.modules.institution.domain.Institution;
import com.credchain.modules.institution.infrastructure.InstitutionRepository;
import com.credchain.modules.verification.api.dto.VerificationResponse;
import com.credchain.modules.verification.domain.VerificationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.utils.Numeric;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Public check of one certificate by its hash (what the QR code on the PDF points to). No login needed.
 * The blockchain is asked live when it is enabled; the strictest answer wins, so a certificate revoked
 * either in the database or on-chain is always reported as REVOKED.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublicVerificationService {

    private static final Pattern CERT_HASH = Pattern.compile("^0x[0-9a-f]{64}$");

    private final CertificateRepository certificateRepository;
    private final CertificateBatchRepository batchRepository;
    private final InstitutionRepository institutionRepository;
    private final BlockchainProperties blockchainProperties;
    private final ObjectProvider<CredentialRegistryClient> registryProvider;   // absent when the blockchain is off
    private final Clock clock;

    @Transactional(readOnly = true)
    public VerificationResponse verifyByHash(String rawCertHash) {
        String certHash = normalize(rawCertHash);
        Instant now = clock.instant();

        Optional<Certificate> found = certificateRepository.findByCertHash(certHash)
                .filter(c -> c.getStatus() != CertificateStatus.DRAFT && c.getStatus() != CertificateStatus.PENDING);
        if (found.isEmpty()) {
            return VerificationResponse.notFound(certHash, "No certificate with this hash was issued on CredChain.",
                    false, now);
        }
        Certificate certificate = found.get();
        CertificateBatch batch = batchRepository.findById(certificate.getBatchId())
                .orElseThrow(() -> new IllegalStateException("Batch of certificate " + certificate.getId() + " not found"));
        Institution institution = institutionRepository.findById(certificate.getInstitutionId())
                .orElseThrow(() -> new IllegalStateException("Institution of certificate " + certificate.getId() + " not found"));

        Integer onChain = readChain(certificate, batch);
        VerificationStatus status = decide(certificate, batch, onChain, now);
        if (status == VerificationStatus.NOT_FOUND) {
            log.error("Certificate {} is in the database but NOT FOUND on-chain", certificate.getId());
            return VerificationResponse.notFound(certHash,
                    "This certificate is not recorded on the blockchain. Do not trust it.", true, now);
        }
        return new VerificationResponse(certHash, status, message(status, onChain != null), onChain != null, now,
                details(certificate, batch, institution), revocation(certificate, status), blockchain(batch));
    }

    // ---------- decision ----------

    /**
     * The strictest answer wins.
     * onChain = null means the blockchain could not be asked (disabled or unreachable): the database decides.
     */
    static VerificationStatus decide(Certificate certificate, CertificateBatch batch, Integer onChain, Instant now) {
        boolean revokedInDatabase = certificate.getStatus() == CertificateStatus.REVOKED
                || certificate.getStatus() == CertificateStatus.REVOCATION_PENDING;
        if (revokedInDatabase || (onChain != null && onChain == CredentialRegistryClient.STATUS_REVOKED)) {
            return VerificationStatus.REVOKED;
        }
        if (onChain != null && onChain == CredentialRegistryClient.STATUS_NOT_FOUND) {
            return VerificationStatus.NOT_FOUND;
        }
        boolean expiredInDatabase = batch.getExpiresAt() != null && !batch.getExpiresAt().isAfter(now);
        if (expiredInDatabase || (onChain != null && onChain == CredentialRegistryClient.STATUS_EXPIRED)) {
            return VerificationStatus.EXPIRED;
        }
        return VerificationStatus.VALID;
    }

    /** Live on-chain status, or null if the blockchain is off or not reachable right now. */
    private Integer readChain(Certificate certificate, CertificateBatch batch) {
        CredentialRegistryClient registry = registryProvider.getIfAvailable();
        if (registry == null) {
            return null;
        }
        try {
            return registry.verifyInBatch(
                    Numeric.hexStringToByteArray(batch.getMerkleRoot()),
                    Numeric.hexStringToByteArray(certificate.getCertHash()),
                    MerkleProofJson.parse(certificate.getMerkleProof()));
        } catch (BlockchainException e) {
            log.warn("Public verification of {} used the database only, blockchain not reachable: {}",
                    certificate.getId(), e.getMessage());
            return null;
        }
    }

    private static String message(VerificationStatus status, boolean blockchainChecked) {
        String source = blockchainChecked
                ? "Confirmed on the blockchain."
                : "The blockchain could not be checked right now; this result is from the CredChain database.";
        return switch (status) {
            case VALID -> "This certificate is genuine and valid. " + source;
            case REVOKED -> "This certificate was revoked by the issuing institution. Do not accept it.";
            case EXPIRED -> "This certificate was genuine but has expired. " + source;
            case NOT_FOUND -> "No certificate with this hash was issued on CredChain.";
            case FAKE -> "This certificate is not genuine. Do not accept it.";   // only used by the PDF check
        };
    }

    // ---------- response parts ----------

    private static VerificationResponse.CertificateDetails details(Certificate c, CertificateBatch batch,
                                                                   Institution institution) {
        return new VerificationResponse.CertificateDetails(c.getCertificateNumber(), c.getType(), c.getTitle(),
                c.getProgram(), c.getGrade(), CertificateHasher.formatCgpa(c.getCgpa()), c.getAwardedOn(),
                c.getStudentName(), c.getEnrollmentNo(), institution.getName(), institution.getCode(),
                batch.getExpiresAt());
    }

    private static VerificationResponse.Revocation revocation(Certificate c, VerificationStatus status) {
        if (status != VerificationStatus.REVOKED) {
            return null;
        }
        return new VerificationResponse.Revocation(c.getRevocationReason(), c.getRevokedAt());
    }

    private VerificationResponse.BlockchainRecord blockchain(CertificateBatch batch) {
        return new VerificationResponse.BlockchainRecord(batch.getChainId(), blockchainProperties.contractAddress(),
                batch.getMerkleRoot(), batch.getIssuerAddress(), batch.getTxHash(), batch.getBlockNumber(),
                batch.getAnchoredAt(), ExplorerLinks.tx(batch.getChainId(), batch.getTxHash()));
    }

    /** Accepts upper/lower case and an optional 0x; anything else is a 400. */
    static String normalize(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (!value.startsWith("0x")) {
            value = "0x" + value;
        }
        if (!CERT_HASH.matcher(value).matches()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "Not a certificate hash. It should look like 0x followed by 64 hex characters.");
        }
        return value;
    }
}
