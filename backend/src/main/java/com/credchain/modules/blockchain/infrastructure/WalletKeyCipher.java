package com.credchain.modules.blockchain.infrastructure;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

/**
 * Encrypts institution wallet private keys at rest with AES-256-GCM.
 *
 * Stored format:  v1:base64( 12-byte random IV || ciphertext || 16-byte auth tag )
 *
 * - Authenticated encryption: any modified byte makes decryption fail.
 * - The "context" (e.g. the institution id) is bound as Additional Authenticated Data,
 *   so an encrypted key copied onto another record cannot be decrypted there.
 * - The "v1" prefix allows a future master-key rotation.
 */
public final class WalletKeyCipher {

    private static final String FORMAT_PREFIX = "v1:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;


    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public WalletKeyCipher(String base64Key) {
        byte[] raw = decodeKey(base64Key);
        this.key = new SecretKeySpec(raw, "AES");
        Arrays.fill(raw, (byte) 0);
    }

    /** Used by configuration validation: true if the value is Base64 for exactly 32 bytes. */
    public static boolean isValidKey(String base64Key) {
        try {
            byte[] raw = decodeKey(base64Key);
            Arrays.fill(raw, (byte) 0);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public String encrypt(byte[] plaintext, String context) {
        Objects.requireNonNull(plaintext, "plaintext");
        byte[] aad = contextBytes(context);
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad);
            byte[] ciphertext = cipher.doFinal(plaintext);
            byte[] payload = ByteBuffer.allocate(IV_BYTES + ciphertext.length).put(iv).put(ciphertext).array();

            return FORMAT_PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Wallet key encryption failed", e);
        }
    }

    public byte[] decrypt(String token, String context) {
        byte[] aad = contextBytes(context);
        if (token == null || !token.startsWith(FORMAT_PREFIX)) {
            throw new WalletKeyDecryptionException("Unsupported encrypted key format");
        }
        byte[] payload;
        try {
            payload = Base64.getDecoder().decode(token.substring(FORMAT_PREFIX.length()));
        } catch (IllegalArgumentException e) {
            throw new WalletKeyDecryptionException("Encrypted key is not valid Base64");
        }
        if (payload.length <= IV_BYTES + TAG_BITS / 8) {
            throw new WalletKeyDecryptionException("Encrypted key is too short");
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, payload, 0, IV_BYTES));
            cipher.updateAAD(aad);
            return cipher.doFinal(payload, IV_BYTES, payload.length - IV_BYTES);
        } catch (AEADBadTagException e) {
            throw new WalletKeyDecryptionException(
                    "Encrypted key was modified, belongs to another record, or the master key is wrong");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Wallet key decryption failed", e);
        }
    }


    private static byte[] decodeKey(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalArgumentException("Wallet encryption key is missing");
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Wallet encryption key is not valid Base64");
        }
        if (raw.length != KEY_BYTES) {
            Arrays.fill(raw, (byte) 0);
            throw new IllegalArgumentException("Wallet encryption key must be 32 bytes (256 bits)");
        }
        return raw;
    }

    private static byte[] contextBytes(String context) {
        if (context == null || context.isBlank()) {
            throw new IllegalArgumentException("Encryption context (e.g. institution id) is required");
        }
        return context.getBytes(StandardCharsets.UTF_8);
    }

    /** Thrown when a stored key cannot be decrypted; never contains key material. */
    public static class WalletKeyDecryptionException extends RuntimeException {
        public WalletKeyDecryptionException(String message) {
            super(message);
        }
    }
}