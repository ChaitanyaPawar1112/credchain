package com.credchain.modules.blockchain.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The expected selectors were read from the dispatcher of the deployed bytecode on Sepolia
 * (0x1EA82E244e3b38Fc294075Ab03D30F5e3E2947b0), so a wrong parameter type in Java fails here.
 */
@DisplayName("CredentialRegistry ABI encoding")
class CredentialRegistryAbiTest {

    private static final String ADDRESS = "0x09941292af95da3dedd9849b077eac1b1142ab69";
    private static final byte[] HASH = new byte[32];
    private static final List<byte[]> PROOF = List.of(new byte[32], new byte[32]);

    @Test
    @DisplayName("function selectors match the deployed contract")
    void selectorsMatchDeployedContract() {
        assertThat(selector(CredentialRegistryAbi.addIssuer(ADDRESS))).isEqualTo("0x20694db0");
        assertThat(selector(CredentialRegistryAbi.removeIssuer(ADDRESS))).isEqualTo("0x47bc7093");
        assertThat(selector(CredentialRegistryAbi.isIssuer(ADDRESS))).isEqualTo("0x877b9a67");
        assertThat(selector(CredentialRegistryAbi.issueCredential(HASH, 0))).isEqualTo("0x51ed4861");
        assertThat(selector(CredentialRegistryAbi.issueBatch(HASH, 3, 0))).isEqualTo("0x97d7594a");
        assertThat(selector(CredentialRegistryAbi.revokeCredential(HASH, 1))).isEqualTo("0x04211749");
        assertThat(selector(CredentialRegistryAbi.revokeBatch(HASH, 1))).isEqualTo("0xa279add6");
        assertThat(selector(CredentialRegistryAbi.revokeBatchEntry(HASH, HASH, PROOF, 1))).isEqualTo("0xada7c533");
        assertThat(selector(CredentialRegistryAbi.verify(HASH))).isEqualTo("0x75e36616");

        assertThat(selector(CredentialRegistryAbi.verifyInBatch(HASH, HASH, PROOF))).isEqualTo("0xcb320cc6");
    }

    @Test
    @DisplayName("addIssuer encodes the address as one left-padded 32-byte word")
    void addIssuerEncoding() {
        String encoded = CredentialRegistryAbi.encode(CredentialRegistryAbi.addIssuer(ADDRESS));
        assertThat(encoded).isEqualTo("0x20694db0" + "0".repeat(24) + ADDRESS.substring(2));
    }

    @Test
    @DisplayName("issueBatch encodes root, count and expiry as three 32-byte words")
    void issueBatchEncoding() {
        String encoded = CredentialRegistryAbi.encode(CredentialRegistryAbi.issueBatch(HASH, 3, 0));
        assertThat(encoded).hasSize(2 + 8 + 3 * 64);
        assertThat(encoded.substring(10 + 64, 10 + 128)).isEqualTo("0".repeat(63) + "3");
    }

    private static String selector(org.web3j.abi.datatypes.Function function) {
        return CredentialRegistryAbi.encode(function).substring(0, 10);
    }
}