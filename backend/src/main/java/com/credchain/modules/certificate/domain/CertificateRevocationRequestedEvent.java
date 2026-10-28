package com.credchain.modules.certificate.domain;

import java.util.UUID;

/** Published (inside the same transaction) when a college admin revokes a certificate. */
public record CertificateRevocationRequestedEvent(UUID certificateId) {
}
