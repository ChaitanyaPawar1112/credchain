package com.credchain.modules.notification.domain;

/** Kinds of in-app notification. Stored by name: new ones may be added, existing names never change. */
public enum NotificationType {
    /** A college admin's account was created on approval. */
    WELCOME,
    /** One of the student's certificates was recorded on the blockchain. */
    CERTIFICATE_ISSUED,
    /** The college revoked one of the student's certificates. */
    CERTIFICATE_REVOKED,
    /** Someone checked one of the student's certificates and it was genuine. */
    CERTIFICATE_CHECKED,
    /** A college's batch was recorded on the blockchain. */
    BATCH_ANCHORED,
    /** A fake, edited or high-risk copy of one of the college's certificates was checked. */
    FAKE_COPY_CHECKED,
    /** A college's AI trust score fell to "At risk" (to super admins). */
    COLLEGE_AT_RISK
}
