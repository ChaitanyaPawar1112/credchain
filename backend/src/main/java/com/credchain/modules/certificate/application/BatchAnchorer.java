package com.credchain.modules.certificate.application;

import com.credchain.modules.blockchain.contract.CredentialRegistryClient;
import com.credchain.modules.blockchain.infrastructure.BlockchainException;
import com.credchain.modules.blockchain.infrastructure.TransactionRevertedException;
import com.credchain.modules.certificate.domain.BatchStatus;
import com.credchain.modules.certificate.domain.Certificate;
import com.credchain.modules.certificate.domain.CertificateBatch;
import com.credchain.modules.certificate.infrastructure.CertificateRepository;
import com.credchain.modules.wallet.application.InstitutionWalletService;
import com.credchain.modules.wallet.domain.InstitutionWallet;
import com.credchain.modules.wallet.infrastructure.InstitutionWalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.time.Clock;

/**
 * Anchors ONE batch on-chain:
 *   QUEUED    -> sign issueBatch with the institution's wallet and broadcast   -> SUBMITTED (tx hash stored)
 *   SUBMITTED -> wait for success + confirmations                               -> ANCHORED, certificates ISSUED
 * A SUBMITTED batch is never re-sent: the worker keeps waiting for the same tx, so nothing is issued twice.
 * Never throws: failures are recorded on the batch with exponential backoff.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.blockchain", name = "enabled", havingValue = "true")
public class BatchAnchorer {

    private final CredentialRegistryClient registry;
    private final InstitutionWalletRepository walletRepository;
    private final InstitutionWalletService walletService;
    private final CertificateRepository certificateRepository;
    private final Clock clock;

    public void process(CertificateBatch batch) {
        try {
            if (batch.getStatus() == BatchStatus.QUEUED) {
                submit(batch);
            }
            if (batch.getStatus() == BatchStatus.SUBMITTED) {
                confirm(batch);
            }
        } catch (TransactionRevertedException e) {
            batch.returnToQueue(e.getMessage(), clock.instant());
            log.warn("Batch {} was rejected on-chain and returned to the queue: {}", batch.getId(), e.getMessage());
        } catch (BlockchainException e) {
            batch.recordFailure(e.getMessage(), clock.instant());
            log.warn("Batch {} not anchored yet (attempt {}), next try at {}: {}",
                    batch.getId(), batch.getAttempts(), batch.getNextAttemptAt(), e.getMessage());
        } catch (RuntimeException e) {
            log.error("Unexpected error while anchoring batch {}", batch.getId(), e);
            batch.recordFailure("Unexpected error: " + e.getClass().getSimpleName(), clock.instant());
        }
    }

    private void submit(CertificateBatch batch) {
        InstitutionWallet wallet = walletRepository.findByInstitutionId(batch.getInstitutionId())
                .filter(InstitutionWallet::isActive)
                .orElseThrow(() -> new BlockchainException("The institution's issuer wallet is not active"));
        if (!wallet.getAddress().equalsIgnoreCase(batch.getIssuerAddress())) {
            throw new BlockchainException("The institution's wallet changed after the batch was queued");
        }

        Credentials issuer = walletService.signingCredentials(wallet);   // decrypted in memory only
        String txHash = registry.submitIssueBatch(issuer, batch.getMerkleRoot(),
                batch.getCertificateCount(), batch.expiresAtEpochSeconds());
        batch.markSubmitted(txHash);
        log.info("Batch {} submitted from {}: tx {}", batch.getId(), wallet.getAddress(), txHash);
    }

    private void confirm(CertificateBatch batch) {
        TransactionReceipt receipt = registry.awaitReceipt(batch.getTxHash(), "issueBatch(" + batch.getMerkleRoot() + ")");
        batch.markAnchored(receipt.getBlockNumber().longValueExact(), clock.instant());

        var certificates = certificateRepository.findAllByBatchIdOrderByCertificateNumber(batch.getId());
        certificates.forEach(Certificate::markIssued);
        log.info("Batch {} ANCHORED in block {}: {} certificates issued",
                batch.getId(), batch.getBlockNumber(), certificates.size());
    }
}