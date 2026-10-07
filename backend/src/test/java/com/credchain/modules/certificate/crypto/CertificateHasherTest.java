package com.credchain.modules.certificate.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Certificate hashing (canonical JSON + keccak256)")
class CertificateHasherTest {

    private static final String SALT = "0x" + "11".repeat(32);
    // "Rāhul Śarmā" written with escapes so the source file encoding never matters
    private static final String NAME = "R\u0101hul \u015Aarm\u0101";

    @Test
    @DisplayName("golden vector: exact canonical JSON and hash (format must never change)")
    void goldenVector() {
        CertificateHasher.Hashed hashed = CertificateHasher.hash(new CertificateHasher.Payload(
                "SIT-AUR", "SIT-AUR-2026-000001", "DEGREE", "Bachelor of Technology",
                "Computer Science and Engineering", "First Class with Distinction", "8.75", "2026-06-30",
                NAME, "2022CS001", SALT));

        assertThat(hashed.canonicalJson()).isEqualTo(
                "{\"awardedOn\":\"2026-06-30\",\"certificateNumber\":\"SIT-AUR-2026-000001\",\"cgpa\":\"8.75\","
                        + "\"enrollmentNo\":\"2022CS001\",\"grade\":\"First Class with Distinction\",\"institution\":\"SIT-AUR\","
                        + "\"program\":\"Computer Science and Engineering\",\"salt\":\"" + SALT + "\",\"studentName\":\"" + NAME + "\","
                        + "\"title\":\"Bachelor of Technology\",\"type\":\"DEGREE\",\"v\":\"1\"}");
        assertThat(hashed.certHash()).isEqualTo("0x3a5639320e72649cd6a6214703e8de7136e667512a6319f320af8fa15e650f53");
    }


    @Test
    @DisplayName("optional empty fields are left out of the payload")
    void optionalFieldsOmitted() {
        CertificateHasher.Hashed hashed = CertificateHasher.hash(new CertificateHasher.Payload(
                "SIT-AUR", "SIT-AUR-2026-000001", "DEGREE", "Bachelor of Technology",
                null, "  ", null, "2026-06-30", NAME, "2022CS001", SALT));

        assertThat(hashed.canonicalJson()).doesNotContain("program", "grade", "cgpa");
        assertThat(hashed.certHash()).isEqualTo("0x259e0c264e8dd03068a8752eae33cfa087a93036d5d7f19300b6522f44338e3e");
    }

    @Test
    @DisplayName("any change, including the salt, gives a completely different hash")
    void sensitivity() {
        CertificateHasher.Payload base = new CertificateHasher.Payload("SIT-AUR", "N-1", "DEGREE", "B.Tech",
                null, null, "8.75", "2026-06-30", "Student", "E1", SALT);
        CertificateHasher.Payload otherCgpa = new CertificateHasher.Payload("SIT-AUR", "N-1", "DEGREE", "B.Tech",
                null, null, "9.75", "2026-06-30", "Student", "E1", SALT);
        CertificateHasher.Payload otherSalt = new CertificateHasher.Payload("SIT-AUR", "N-1", "DEGREE", "B.Tech",
                null, null, "8.75", "2026-06-30", "Student", "E1", CertificateHasher.newSalt());

        String h = CertificateHasher.hash(base).certHash();
        assertThat(CertificateHasher.hash(base).certHash()).isEqualTo(h);   // deterministic
        assertThat(CertificateHasher.hash(otherCgpa).certHash()).isNotEqualTo(h);
        assertThat(CertificateHasher.hash(otherSalt).certHash()).isNotEqualTo(h);
    }

    @Test
    @DisplayName("keccak256 matches ethers.js on the Phase 3 demo certificate A")
    void matchesEthersDemoHash() {
        String demoJson = "{\"institution\":\"DEMO-INSTITUTE\",\"enrollmentNo\":\"DEMO-001\",\"fullName\":\"Demo Student A\","

                + "\"program\":\"B.Tech Computer Science\",\"graduationYear\":2026,\"cgpa\":\"8.75\","
                + "\"runId\":\"2026-10-05T14:17:42.166Z\"}";
        assertThat(CertificateHasher.hashOf(demoJson))
                .isEqualTo("0xaa6f983f999c6820f9ad8a65bf6868a39c68f221317a0a508d6d8a1cbbeca20b");
    }

    @Test
    @DisplayName("salts are 256-bit hex, CGPA always has two decimals, JSON escaping is standard")
    void helpers() {
        assertThat(CertificateHasher.newSalt()).matches("^0x[0-9a-f]{64}$");
        assertThat(CertificateHasher.formatCgpa(new BigDecimal("8.5"))).isEqualTo("8.50");
        assertThat(CertificateHasher.formatCgpa(null)).isNull();
        assertThat(CanonicalJson.write(Map.of("a", "quote\" back\\ nl\n tab\t ctl\u0001")))
                .isEqualTo("{\"a\":\"quote\\\" back\\\\ nl\\n tab\\t ctl\\u0001\"}");
    }
}