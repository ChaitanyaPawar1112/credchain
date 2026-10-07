package com.credchain.modules.certificate.api.dto;

import com.credchain.modules.blockchain.config.ExplorerLinks;
import com.credchain.modules.certificate.domain.BatchStatus;
import com.credchain.modules.certificate.domain.CertificateBatch;

import java.time.Instant;
import java.util.UUID;

public record BatchResponse(
        UUID id,
        String title,
        BatchStatus status,
        int certificateCount,
        Instant expiresAt,
        String merkleRoot,
        String issuerAddress,
        String txHash,
        Long blockNumber,
        Instant queuedAt,
        Instant anchoredAt,
        int attempts,
        String lastError,
        String explorerTxUrl
) {
    /** @param draftCount live number of certificates (used while the batch is still DRAFT) */
    public static BatchResponse from(CertificateBatch b, long draftCount) {
        return new BatchResponse(b.getId(), b.getTitle(), b.getStatus(),
                b.isDraft() ? (int) draftCount : b.getCertificateCount(),
                b.getExpiresAt(), b.getMerkleRoot(), b.getIssuerAddress(), b.getTxHash(), b.getBlockNumber(),
                b.getQueuedAt(), b.getAnchoredAt(), b.getAttempts(), b.getLastError(),
                ExplorerLinks.tx(b.getChainId(), b.getTxHash()));

    }
}