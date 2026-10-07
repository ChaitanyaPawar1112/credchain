package com.credchain.modules.certificate.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.web3j.utils.Numeric;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test vectors = the batch anchored on Sepolia in Phase 3 with OpenZeppelin's StandardMerkleTree
 * (contracts/demo/demo-result-chain-11155111.json, tx 0x109400e1...c2d9).
 */
@DisplayName("MerkleTree (OpenZeppelin-compatible)")
class MerkleTreeTest {

    private static final String ROOT_ON_CHAIN = "0x6560f6d4e6755457ab7ccfb251a3676b13b9dbfad9d1e74db4758d2de4b2da49";
    private static final String H101 = "0xe56067163b5789b3d34974aa58c4f6684065dd5867e2b6c7db0f2432ed38a0e2";
    private static final String H102 = "0x79443b7ba7276bacd71e379f9cfb3990b08a8d223614cf0a946e768be2fcd5d5";
    private static final String H103 = "0x5209fcd0b9a54af55becfadcad49a4a28a7e32e63d0c3975b6ce45e1acc24521";

    private final MerkleTree tree = MerkleTree.build(List.of(bytes(H101), bytes(H102), bytes(H103)));

    @Test
    @DisplayName("root equals the root anchored on Sepolia")
    void rootMatchesChain() {
        assertThat(tree.rootHex()).isEqualTo(ROOT_ON_CHAIN);
    }

    @Test

    @DisplayName("proofs equal the ones produced by OpenZeppelin")
    void proofsMatchOpenZeppelin() {
        assertThat(tree.proofHex(H101)).containsExactly(
                "0xa655fcfb03eacc28d58de9ab2c1dacaec0afb363386c91224ce9faf3038ada4a",
                "0xf30ae32756f759eb0232e4a883215faef31eb9bde3d2cf672775d38c7c2d670a");
        assertThat(tree.proofHex(H102)).containsExactly(
                "0x8e9ba9b6648157ffae23adfd26f13d9a1b4e48c37c92a5bd3a8229db97b1f232",
                "0xf30ae32756f759eb0232e4a883215faef31eb9bde3d2cf672775d38c7c2d670a");
        assertThat(tree.proofHex(H103)).containsExactly(
                "0x9ba24e0158aceedecf2cb695d81b53eb92def807da07f56133b8b0d7e3b062bc");
    }

    @Test
    @DisplayName("verify accepts members and rejects a tampered hash")
    void verify() {
        byte[] root = tree.root();
        assertThat(MerkleTree.verify(tree.proof(bytes(H102)), root, bytes(H102))).isTrue();

        byte[] tampered = bytes(H102);
        tampered[31] ^= 0x01;
        assertThat(MerkleTree.verify(tree.proof(bytes(H102)), root, tampered)).isFalse();
    }

    @Test
    @DisplayName("single certificate: root is its leaf, proof is empty; empty or duplicate input rejected")
    void edgeCases() {
        MerkleTree single = MerkleTree.build(List.of(bytes(H101)));
        assertThat(single.root()).isEqualTo(MerkleTree.leaf(bytes(H101)));
        assertThat(single.proof(bytes(H101))).isEmpty();
        assertThat(MerkleTree.verify(List.of(), single.root(), bytes(H101))).isTrue();

        assertThatThrownBy(() -> MerkleTree.build(List.of())).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> MerkleTree.build(List.of(bytes(H101), bytes(H101))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static byte[] bytes(String hex) {
        return Numeric.hexStringToByteArray(hex);
    }
}