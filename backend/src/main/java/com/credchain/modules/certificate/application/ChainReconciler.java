package com.credchain.modules.certificate.application;

import com.credchain.modules.blockchain.contract.CredentialRegistryClient;
import com.credchain.modules.certificate.crypto.MerkleProofJson;
import com.credchain.modules.certificate.domain.Certificate;
import com.credchain.modules.certificate.domain.CertificateBatch;
import com.credchain.modules.certificate.domain.CertificateStatus;
import com.credchain.modules.certificate.domain.ChainCheckResult;
import com.credchain.modules.certificate.infrastructure.CertificateBatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.web3j.utils.Numeric;

import java.time.Clock;

/**
 * Compares ONE certificate in the database with the smart contract (free view call, no gas).
 * It never changes the certificate's status: a mismatch is recorded and logged for a human to look at.
 * A BlockchainException (RPC down) is NOT caught: the job stops and the certificate is checked next time.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.blockchain", name = "enabled", havingValue = "true")
public class ChainReconciler {

    private final CredentialRegistryClient registry;
    private final CertificateBatchRepository batchRepository;
    private final Clock clock;

    /** What the comparison found. */
    public record Outcome(ChainCheckResult result, String note) {

        static Outcome match(String note) {
            return new Outcome(ChainCheckResult.MATCH, note);
        }

        static Outcome mismatch(String note) {
            return new Outcome(ChainCheckResult.MISMATCH, note);
        }
    }

    public ChainCheckResult check(Certificate certificate) {
        Outcome outcome;
        try {
            CertificateBatch batch = batchRepository.findById(certificate.getBatchId())
                    .orElseThrow(() -> new IllegalStateException("Batch of the certificate not found"));
            int onChain = registry.verifyInBatch(
                    Numeric.hexStringToByteArray(batch.getMerkleRoot()),
                    Numeric.hexStringToByteArray(certificate.getCertHash()),
                    MerkleProofJson.parse(certificate.getMerkleProof()));
            outcome = compare(certificate.getStatus(), onChain);
        } catch (IllegalStateException | IllegalArgumentException e) {
            // broken data in our own database (missing batch, unreadable proof): that is a mismatch too
            outcome = Outcome.mismatch("Cannot check: " + e.getMessage());
        }

        certificate.recordChainCheck(outcome.result(), outcome.note(), clock.instant());
        if (outcome.result() == ChainCheckResult.MISMATCH) {
            log.error("RECONCILIATION MISMATCH for certificate {} ({}): {}",
                    certificate.getCertificateNumber(), certificate.getId(), outcome.note());
        }
        return outcome.result();
    }

    /** The rules: which on-chain status is acceptable for each database status. */
    static Outcome compare(CertificateStatus database, int onChain) {
        String chain = chainStatusName(onChain);
        return switch (database) {
            case ISSUED -> switch (onChain) {
                case CredentialRegistryClient.STATUS_VALID -> Outcome.match("VALID on-chain");
                case CredentialRegistryClient.STATUS_EXPIRED -> Outcome.match("EXPIRED on-chain (expiry date passed)");
                default -> Outcome.mismatch("ISSUED in the database but " + chain + " on-chain");
            };
            case REVOCATION_PENDING -> switch (onChain) {
                case CredentialRegistryClient.STATUS_VALID, CredentialRegistryClient.STATUS_REVOKED,
                     CredentialRegistryClient.STATUS_EXPIRED -> Outcome.match("revocation in progress, " + chain + " on-chain");
                default -> Outcome.mismatch("REVOCATION_PENDING in the database but " + chain + " on-chain");
            };
            case REVOKED -> onChain == CredentialRegistryClient.STATUS_REVOKED
                    ? Outcome.match("REVOKED on-chain")
                    : Outcome.mismatch("REVOKED in the database but " + chain + " on-chain");
            case DRAFT, PENDING -> Outcome.mismatch("certificate is " + database + "; it should not be checked yet");
        };
    }

    private static String chainStatusName(int onChain) {
        return switch (onChain) {
            case CredentialRegistryClient.STATUS_NOT_FOUND -> "NOT FOUND";
            case CredentialRegistryClient.STATUS_VALID -> "VALID";
            case CredentialRegistryClient.STATUS_REVOKED -> "REVOKED";
            case CredentialRegistryClient.STATUS_EXPIRED -> "EXPIRED";
            default -> "UNKNOWN (" + onChain + ")";
        };
    }
}