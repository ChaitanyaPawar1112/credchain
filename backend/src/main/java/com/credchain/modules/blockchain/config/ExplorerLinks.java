package com.credchain.modules.blockchain.config;

import java.util.Map;

/** Block-explorer links for the networks we support. */
public final class ExplorerLinks {

    private static final Map<Long, String> EXPLORERS = Map.of(
            1L, "https://etherscan.io",
            11155111L, "https://sepolia.etherscan.io");

    private ExplorerLinks() {
    }

    public static String tx(Long chainId, String txHash) {
        String base = chainId == null ? null : EXPLORERS.get(chainId);
        return (base == null || txHash == null) ? null : base + "/tx/" + txHash;
    }

    public static String address(Long chainId, String address) {
        String base = chainId == null ? null : EXPLORERS.get(chainId);
        return (base == null || address == null) ? null : base + "/address/" + address;
    }
}