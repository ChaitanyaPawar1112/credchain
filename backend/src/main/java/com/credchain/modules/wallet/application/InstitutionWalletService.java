package com.credchain.modules.wallet.application;

import com.credchain.modules.blockchain.infrastructure.WalletKeyCipher;
import com.credchain.modules.institution.domain.InstitutionStatusChangedEvent;
import com.credchain.modules.wallet.domain.InstitutionWallet;
import com.credchain.modules.wallet.infrastructure.InstitutionWalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.utils.Numeric;

import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages the custodial issuer wallet of each institution.
 * Reacts to institution status changes inside the same transaction, so an approved
 * institution can never exist without a wallet.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InstitutionWalletService {

    private static final int PRIVATE_KEY_BYTES = 32;

    private final InstitutionWalletRepository walletRepository;
    private final WalletKeyCipher walletKeyCipher;
    private final Clock clock;

    @EventListener
    @Transactional
    public void onInstitutionStatusChanged(InstitutionStatusChangedEvent event) {
        switch (event.newStatus()) {
            case APPROVED -> requestActivation(event.institutionId());
            case SUSPENDED -> requestDeactivation(event.institutionId());
            default -> { /* PENDING / REJECTED: no wallet needed */ }
        }
    }

    /**
     * Makes sure an approved institution has a wallet queued for activation.
     * Used for institutions approved before wallets existed. Safe to call repeatedly.
     */
    @Transactional
    public void ensureWallet(UUID institutionId) {
        requestActivation(institutionId);
    }

    @Transactional(readOnly = true)
    public Optional<InstitutionWallet> findByInstitution(UUID institutionId) {
        return walletRepository.findByInstitutionId(institutionId);
    }

    /**
     * Decrypts the wallet key for signing. Only the blockchain worker may call this;
     * the returned Credentials must never be logged or returned by an API.
     */
    public Credentials signingCredentials(InstitutionWallet wallet) {
        byte[] privateKey = walletKeyCipher.decrypt(
                wallet.encryptedPrivateKey(), InstitutionWallet.encryptionContext(wallet.getInstitutionId()));
        try {
            Credentials credentials = Credentials.create(ECKeyPair.create(privateKey));
            if (!credentials.getAddress().equalsIgnoreCase(wallet.getAddress())) {
                throw new IllegalStateException("Decrypted key does not match wallet " + wallet.getAddress());
            }
            return credentials;
        } finally {
            Arrays.fill(privateKey, (byte) 0);
        }
    }

    // ---------- internals ----------

    private void requestActivation(UUID institutionId) {
        Instant now = clock.instant();
        walletRepository.findByInstitutionId(institutionId).ifPresentOrElse(
                wallet -> {
                    wallet.requestActivation(now);
                    log.info("Issuer wallet {} of institution {} queued for activation", wallet.getAddress(), institutionId);
                },
                () -> {
                    InstitutionWallet wallet = walletRepository.save(generateWallet(institutionId, now));
                    log.info("Issuer wallet {} created for institution {}", wallet.getAddress(), institutionId);
                });
    }

    private void requestDeactivation(UUID institutionId) {
        walletRepository.findByInstitutionId(institutionId).ifPresent(wallet -> {
            wallet.requestDeactivation(clock.instant());
            log.info("Issuer wallet {} of institution {} queued for deactivation", wallet.getAddress(), institutionId);
        });
    }

    private InstitutionWallet generateWallet(UUID institutionId, Instant now) {
        ECKeyPair keyPair;
        try {
            keyPair = Keys.createEcKeyPair();   // cryptographically secure random key
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not generate an Ethereum key pair", e);
        }
        String address = "0x" + Keys.getAddress(keyPair);
        byte[] privateKey = Numeric.toBytesPadded(keyPair.getPrivateKey(), PRIVATE_KEY_BYTES);
        try {
            String encrypted = walletKeyCipher.encrypt(privateKey, InstitutionWallet.encryptionContext(institutionId));
            return InstitutionWallet.createCustodial(institutionId, address, encrypted, now);
        } finally {
            Arrays.fill(privateKey, (byte) 0);
        }
    }
}