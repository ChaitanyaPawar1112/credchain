package com.credchain.modules.verification.domain;

import java.util.UUID;

/** Published (inside the verification-log transaction) when a public check was about a real issued certificate. */
public record CertificateCheckedEvent(UUID certificateId, VerificationMethod method, VerificationStatus result) {
}
