package com.credchain.modules.certificate.crypto;

import org.web3j.utils.Numeric;

import java.util.Arrays;
import java.util.List;

/** Reads the stored Merkle proof: a JSON array of 0x-prefixed 32-byte hashes, e.g. ["0xab..","0xcd.."]. */
public final class MerkleProofJson {

    private MerkleProofJson() {
    }

    public static List<byte[]> parse(String json) {
        if (json == null) {
            throw new IllegalArgumentException("Merkle proof is missing");
        }
        String body = json.trim();
        if (!body.startsWith("[") || !body.endsWith("]")) {
            throw new IllegalArgumentException("Merkle proof is not a JSON array");
        }
        body = body.substring(1, body.length() - 1).trim();
        if (body.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(body.split(","))
                .map(s -> s.trim().replace("\"", ""))
                .map(hex -> {
                    byte[] bytes = Numeric.hexStringToByteArray(hex);
                    if (bytes.length != 32) {
                        throw new IllegalArgumentException("Merkle proof entry is not 32 bytes");
                    }

                    return bytes;
                })
                .toList();
    }
}