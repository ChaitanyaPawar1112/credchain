package com.credchain.modules.verification.domain;

/** How a verifier checked a certificate. */
public enum VerificationMethod {
    /** By hash: QR code, link or typed hash. */
    HASH,
    /** By uploading the PDF. */
    PDF,
    /** By uploading a photo, scan or other document (AI fraud checks, Phase 7). */
    DOCUMENT
}
