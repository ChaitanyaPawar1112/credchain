package com.credchain.modules.blockchain.contract;

import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint32;
import org.web3j.abi.datatypes.generated.Uint64;
import org.web3j.abi.datatypes.generated.Uint8;

import java.util.List;

/**
 * Java description of the CredentialRegistry v1.0.0 functions (see backend/src/main/resources/blockchain/CredentialRegistry.abi).
 * Selectors are verified against the deployed bytecode in CredentialRegistryAbiTest.
 */
public final class CredentialRegistryAbi {

    private CredentialRegistryAbi() {
    }

    // ---------- admin ----------

    public static Function addIssuer(String issuer) {
        return new Function("addIssuer", List.<Type>of(new Address(issuer)), List.of());
    }

    public static Function removeIssuer(String issuer) {
        return new Function("removeIssuer", List.<Type>of(new Address(issuer)), List.of());
    }

    public static Function isIssuer(String account) {
        return new Function("isIssuer", List.<Type>of(new Address(account)),
                List.<TypeReference<?>>of(new TypeReference<Bool>() {}));
    }

    // ---------- issuing ----------

    /** expiresAt: unix seconds, 0 = never expires. */
    public static Function issueCredential(byte[] certHash, long expiresAt) {
        return new Function("issueCredential",
                List.<Type>of(new Bytes32(certHash), new Uint64(expiresAt)), List.of());
    }

    public static Function issueBatch(byte[] merkleRoot, long count, long expiresAt) {
        return new Function("issueBatch",
                List.<Type>of(new Bytes32(merkleRoot), new Uint32(count), new Uint64(expiresAt)), List.of());
    }

    // ---------- revocation (reason: 0 UNSPECIFIED, 1 ISSUED_IN_ERROR, 2 FRAUD, 3 SUPERSEDED, 4 OTHER) ----------

    public static Function revokeCredential(byte[] certHash, int reason) {
        return new Function("revokeCredential",
                List.<Type>of(new Bytes32(certHash), new Uint8(reason)), List.of());
    }

    public static Function revokeBatch(byte[] merkleRoot, int reason) {
        return new Function("revokeBatch",
                List.<Type>of(new Bytes32(merkleRoot), new Uint8(reason)), List.of());
    }

    public static Function revokeBatchEntry(byte[] merkleRoot, byte[] certHash, List<byte[]> proof, int reason) {
        return new Function("revokeBatchEntry",
                List.<Type>of(new Bytes32(merkleRoot), new Bytes32(certHash), proofArray(proof), new Uint8(reason)),
                List.of());
    }

    // ---------- verification (free view calls) ----------

    /** Returns (uint8 status, address issuer, uint64 issuedAt, uint64 expiresAt). */
    public static Function verify(byte[] certHash) {
        return new Function("verify", List.<Type>of(new Bytes32(certHash)), verifyOutputs());
    }

    public static Function verifyInBatch(byte[] merkleRoot, byte[] certHash, List<byte[]> proof) {
        return new Function("verifyInBatch",
                List.<Type>of(new Bytes32(merkleRoot), new Bytes32(certHash), proofArray(proof)), verifyOutputs());
    }

    public static String encode(Function function) {
        return FunctionEncoder.encode(function);
    }

    // ---------- helpers ----------

    private static DynamicArray<Bytes32> proofArray(List<byte[]> proof) {
        return new DynamicArray<>(Bytes32.class, proof.stream().map(Bytes32::new).toList());
    }

    private static List<TypeReference<?>> verifyOutputs() {
        return List.of(
                new TypeReference<Uint8>() {},
                new TypeReference<Address>() {},
                new TypeReference<Uint64>() {},
                new TypeReference<Uint64>() {});
    }
}