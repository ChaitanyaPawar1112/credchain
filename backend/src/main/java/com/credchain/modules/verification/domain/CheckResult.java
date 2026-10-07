package com.credchain.modules.verification.domain;

/** Outcome of one step of the PDF upload check. */
public enum CheckResult {
    PASSED,
    FAILED,
    /** Could not be run right now (for example file storage is down). */
    SKIPPED
}
