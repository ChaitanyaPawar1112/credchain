package com.credchain.modules.certificate.crypto;

import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Merkle tree identical to OpenZeppelin's StandardMerkleTree for leaf type ["bytes32"]:
 *   leaf    = keccak256(keccak256(abi.encode(certHash)))      (= CredentialRegistry.leafOf)
 *   node    = keccak256(sorted(a, b))                         (= MerkleProof commutative hashing)
 *   layout  = leaves sorted, stored at the end of the array in reverse order, parents at (i-1)/2
 * Verified against the batch anchored on Sepolia in Phase 3 (see MerkleTreeTest).
 */
public final class MerkleTree {

    private static final int HASH_BYTES = 32;

    private final byte[][] tree;
    private final Map<String, Integer> treeIndexByCertHash;

    private MerkleTree(byte[][] tree, Map<String, Integer> treeIndexByCertHash) {
        this.tree = tree;
        this.treeIndexByCertHash = treeIndexByCertHash;
    }

    /** @param certHashes 32-byte certificate hashes (order does not matter) */
    public static MerkleTree build(List<byte[]> certHashes) {
        if (certHashes == null || certHashes.isEmpty()) {
            throw new IllegalArgumentException("At least one certificate hash is required");
        }
        record Entry(byte[] certHash, byte[] leaf) {
        }

        List<Entry> entries = new ArrayList<>(certHashes.size());
        Map<String, Integer> seen = new HashMap<>();
        for (byte[] certHash : certHashes) {
            if (seen.put(Numeric.toHexString(require32(certHash)), 0) != null) {
                throw new IllegalArgumentException("Duplicate certificate hash in batch");
            }
            entries.add(new Entry(certHash.clone(), leaf(certHash)));
        }
        entries.sort((a, b) -> Arrays.compareUnsigned(a.leaf(), b.leaf()));

        int n = entries.size();
        byte[][] tree = new byte[2 * n - 1][];
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < n; i++) {
            int position = tree.length - 1 - i;
            tree[position] = entries.get(i).leaf();
            index.put(Numeric.toHexString(entries.get(i).certHash()), position);
        }
        for (int i = tree.length - 1 - n; i >= 0; i--) {
            tree[i] = hashPair(tree[2 * i + 1], tree[2 * i + 2]);
        }
        return new MerkleTree(tree, index);
    }

    public byte[] root() {
        return tree[0].clone();
    }

    public String rootHex() {
        return Numeric.toHexString(tree[0]);
    }

    public int size() {
        return treeIndexByCertHash.size();
    }

    /** Sibling hashes from the leaf up to the root. Empty for a single-certificate batch. */
    public List<byte[]> proof(byte[] certHash) {
        Integer position = treeIndexByCertHash.get(Numeric.toHexString(require32(certHash)));
        if (position == null) {
            throw new IllegalArgumentException("Certificate hash is not in this tree");
        }
        List<byte[]> proof = new ArrayList<>();
        int j = position;
        while (j > 0) {
            int sibling = (j % 2 == 1) ? j + 1 : j - 1;
            proof.add(tree[sibling].clone());
            j = (j - 1) / 2;
        }
        return proof;
    }

    public List<String> proofHex(String certHashHex) {
        return proof(Numeric.hexStringToByteArray(certHashHex)).stream().map(Numeric::toHexString).toList();
    }

    /** leaf = keccak256(keccak256(certHash)), same as CredentialRegistry.leafOf(certHash). */
    public static byte[] leaf(byte[] certHash) {
        return Hash.sha3(Hash.sha3(require32(certHash)));
    }

    /** Same algorithm as OpenZeppelin MerkleProof.verify. */
    public static boolean verify(List<byte[]> proof, byte[] root, byte[] certHash) {
        byte[] computed = leaf(certHash);
        for (byte[] sibling : proof) {
            computed = hashPair(computed, require32(sibling));
        }
        return Arrays.equals(computed, root);
    }

    private static byte[] hashPair(byte[] a, byte[] b) {
        byte[] joined = new byte[HASH_BYTES * 2];
        boolean aFirst = Arrays.compareUnsigned(a, b) <= 0;
        System.arraycopy(aFirst ? a : b, 0, joined, 0, HASH_BYTES);
        System.arraycopy(aFirst ? b : a, 0, joined, HASH_BYTES, HASH_BYTES);
        return Hash.sha3(joined);
    }

    private static byte[] require32(byte[] value) {
        if (value == null || value.length != HASH_BYTES) {
            throw new IllegalArgumentException("Expected a 32-byte hash");
        }
        return value;
    }
}