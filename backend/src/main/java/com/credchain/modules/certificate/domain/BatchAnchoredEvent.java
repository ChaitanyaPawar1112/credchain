package com.credchain.modules.certificate.domain;

import java.util.UUID;

/**
 * Published (inside the anchoring transaction) when a batch is confirmed on the blockchain and its certificates
 * become ISSUED. Other modules react to it without the certificate module knowing about them.
 */
public record BatchAnchoredEvent(UUID batchId, UUID institutionId, String title, int certificateCount) {
}
