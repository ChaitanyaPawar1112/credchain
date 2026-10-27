package com.credchain.modules.fraud.domain;

/** How much the platform trusts a college's recent activity. */
public enum TrustLevel {
    /** Score 80-100: nothing unusual. */
    TRUSTED,
    /** Score 50-79: worth a look. */
    WATCH,
    /** Score 0-49: unusual activity or many forged copies; check before trusting new certificates. */
    AT_RISK
}
