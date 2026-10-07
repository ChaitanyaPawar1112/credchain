package com.credchain.modules.certificate.application;

import com.credchain.modules.blockchain.contract.CredentialRegistryClient;
import com.credchain.modules.blockchain.infrastructure.BlockchainException;
import com.credchain.modules.certificate.crypto.MerkleProofJson;
import com.credchain.modules.certificate.domain.Certificate;
import com.credchain.modules.certificate.domain.CertificateBatch;
import com.credchain.modules.certificate.infrastructure.CertificateBatchRepository;
import com.credchain.modules.wallet.application.InstitutionWalletService;
import com.credchain.modules.wallet.domain.InstitutionWallet;
import com.credchain.modules.wallet.infrastructure.InstitutionWalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.web3j.crypto.Credentials;
import org.web3j.utils.Numeric;

import java.time.Clock;
import java.util.List;

/**
 * Revokes ONE certificate on-chain with revokeBatchEntry, signed by the issuing institution's wallet.
 * Checks the chain first: if it is already REVOKED there, nothing is sent again.
 * Never throws: failures are recorded on the certificate with exponential backoff.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.blockchain", name = "enabled", havingValue = "true")
public class CertificateRevoker {


    private final CredentialRegistryClient registry;
    private final CertificateBatchRepository batchRepository;
    private final InstitutionWalletRepository walletRepository;
    private final InstitutionWalletService walletService;
    private final Clock clock;

    public void process(Certificate certificate) {
        try {
            CertificateBatch batch = batchRepository.findById(certificate.getBatchId())
                    .orElseThrow(() -> new BlockchainException("Batch of the certificate not found"));
            byte[] root = Numeric.hexStringToByteArray(batch.getMerkleRoot());
            byte[] certHash = Numeric.hexStringToByteArray(certificate.getCertHash());
            List<byte[]> proof = MerkleProofJson.parse(certificate.getMerkleProof());

            int onChain = registry.verifyInBatch(root, certHash, proof);
            String revokeTx = null;
            if (onChain != CredentialRegistryClient.STATUS_REVOKED) {
                if (onChain == CredentialRegistryClient.STATUS_NOT_FOUND) {
                    throw new BlockchainException("Certificate is not found on-chain; cannot revoke it");
                }
                InstitutionWallet wallet = walletRepository.findByInstitutionId(certificate.getInstitutionId())
                        .filter(InstitutionWallet::isActive)
                        .orElseThrow(() -> new BlockchainException("The institution's issuer wallet is not active"));
                Credentials issuer = walletService.signingCredentials(wallet);
                revokeTx = registry.revokeBatchEntry(issuer, root, certHash, proof,
                        certificate.getRevocationReason().chainCode()).getTransactionHash();
            }

            certificate.markRevoked(revokeTx, clock.instant());
            log.warn("Certificate {} REVOKED on-chain ({}), tx {}",
                    certificate.getCertificateNumber(), certificate.getRevocationReason(), revokeTx);
        } catch (BlockchainException e) {

            certificate.recordFailure(e.getMessage(), clock.instant());
            log.warn("Revocation of certificate {} failed (attempt {}), next try at {}: {}",
                    certificate.getId(), certificate.getAttempts(), certificate.getNextAttemptAt(), e.getMessage());
        } catch (RuntimeException e) {
            log.error("Unexpected error while revoking certificate {}", certificate.getId(), e);
            certificate.recordFailure("Unexpected error: " + e.getClass().getSimpleName(), clock.instant());
        }
    }
}