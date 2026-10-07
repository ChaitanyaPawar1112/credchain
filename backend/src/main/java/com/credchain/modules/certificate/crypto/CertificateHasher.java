package com.credchain.modules.certificate.crypto;

import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

/**
 * Builds the canonical payload of a certificate and its keccak256 fingerprint (the value stored on-chain).
 *
 * Payload schema v1 (keys sorted when serialized):
 *   v, institution, certificateNumber, type, title, program?, grade?, cgpa?, awardedOn, studentName, enrollmentNo, salt
 * The random salt makes the hash impossible to guess from public facts about a student.
 */
public final class CertificateHasher {

    public static final String SCHEMA_VERSION = "1";
    private static final SecureRandom RANDOM = new SecureRandom();

    private CertificateHasher() {
    }

    /** All values already formatted as strings; optional ones may be null (they are omitted). */
    public record Payload(String institutionCode, String certificateNumber, String type, String title,
                          String program, String grade, String cgpa, String awardedOn,
                          String studentName, String enrollmentNo, String salt) {
    }


    public record Hashed(String canonicalJson, String certHash) {
    }

    /** New 256-bit secret salt, as 0x + 64 hex characters. */
    public static String newSalt() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Numeric.toHexString(bytes);
    }

    /** CGPA always with exactly two decimals ("8.5" -> "8.50"), or null. */
    public static String formatCgpa(BigDecimal cgpa) {
        return cgpa == null ? null : cgpa.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }

    public static Hashed hash(Payload p) {
        Map<String, String> fields = new HashMap<>();
        fields.put("v", SCHEMA_VERSION);
        fields.put("institution", p.institutionCode());
        fields.put("certificateNumber", p.certificateNumber());
        fields.put("type", p.type());
        fields.put("title", p.title());
        fields.put("program", blankToNull(p.program()));
        fields.put("grade", blankToNull(p.grade()));
        fields.put("cgpa", p.cgpa());
        fields.put("awardedOn", p.awardedOn());
        fields.put("studentName", p.studentName());
        fields.put("enrollmentNo", p.enrollmentNo());
        fields.put("salt", p.salt());

        String json = CanonicalJson.write(fields);

        return new Hashed(json, hashOf(json));
    }

    /** keccak256 of the UTF-8 bytes, as 0x + 64 lowercase hex. Used again in Phase 5 to re-check a payload. */
    public static String hashOf(String canonicalJson) {
        return Numeric.toHexString(Hash.sha3(canonicalJson.getBytes(StandardCharsets.UTF_8)));
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}