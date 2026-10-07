package com.credchain.modules.verification.application;

import com.credchain.common.exception.BusinessException;
import com.credchain.modules.certificate.crypto.MerkleTree;
import com.credchain.modules.verification.api.dto.VerificationResponse;
import com.credchain.modules.verification.application.PdfVerificationService.Embedded;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PDF verification rules")
class PdfVerificationServiceTest {

    private static final String CONTRACT = "0x1EA82E244e3b38Fc294075Ab03D30F5e3E2947b0";

    @Test
    @DisplayName("Merkle proof: holds for every certificate of a batch, fails for an outsider or a broken proof")
    void proof() {
        List<byte[]> hashes = List.of(hash("a"), hash("b"), hash("c"));
        MerkleTree tree = MerkleTree.build(hashes);
        String b = Numeric.toHexString(hashes.get(1));

        assertThat(PdfVerificationService.proofHolds(embedded(b, proofJson(tree.proofHex(b)), tree.rootHex()), b)).isTrue();

        String outsider = Numeric.toHexString(hash("x"));
        assertThat(PdfVerificationService.proofHolds(embedded(outsider, proofJson(tree.proofHex(b)), tree.rootHex()), outsider))
                .isFalse();
        assertThat(PdfVerificationService.proofHolds(embedded(b, "not json", tree.rootHex()), b)).isFalse();
    }

    @Test
    @DisplayName("the PDF must name the same batch, network and contract as the official record")
    void recordMismatch() {
        String root = Numeric.toHexString(hash("root"));
        VerificationResponse.BlockchainRecord chain = new VerificationResponse.BlockchainRecord(11155111L, CONTRACT,
                root, "0xissuer", "0xtx", 1L, Instant.now(), null);

        assertThat(PdfVerificationService.recordMismatch(
                new Embedded("h", "p", "[]", root.toUpperCase().replace("0X", "0x"), "11155111", CONTRACT.toLowerCase()), chain))
                .isNull();   // case does not matter
        assertThat(PdfVerificationService.recordMismatch(new Embedded("h", "p", "[]", root, "1", CONTRACT), chain))
                .contains("network");
        assertThat(PdfVerificationService.recordMismatch(
                new Embedded("h", "p", "[]", Numeric.toHexString(hash("other")), "11155111", CONTRACT), chain))
                .contains("batch");
        assertThat(PdfVerificationService.recordMismatch(new Embedded("h", "p", "[]", root, "11155111", "0xdead"), chain))
                .contains("contract");
    }

    @Test
    @DisplayName("embedded data is only accepted when every CredChain key is present")
    void embeddedKeys() {
        Map<String, String> full = Map.of("CredChain-CertHash", "0x1", "CredChain-Payload", "{}", "CredChain-Proof", "[]",
                "CredChain-MerkleRoot", "0x2", "CredChain-ChainId", "1", "CredChain-Contract", CONTRACT);
        assertThat(PdfVerificationService.embedded(full)).isPresent();
        assertThat(PdfVerificationService.embedded(Map.of("Title", "My certificate"))).isEmpty();
    }

    @Test
    @DisplayName("non-PDF bytes are rejected before any parsing")
    void notAPdf() {
        assertThatThrownBy(() -> PdfVerificationService.readInfo("hello".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> PdfVerificationService.readInfo("%PDF-1.4 broken".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(BusinessException.class);
    }

    private static Embedded embedded(String certHash, String proof, String root) {
        return new Embedded(certHash, "{}", proof, root, "11155111", CONTRACT);
    }

    private static String proofJson(List<String> proof) {
        return proof.stream().map(p -> "\"" + p + "\"").reduce((x, y) -> x + "," + y).map(s -> "[" + s + "]").orElse("[]");
    }

    private static byte[] hash(String seed) {
        return Hash.sha3(seed.getBytes(StandardCharsets.UTF_8));
    }
}
