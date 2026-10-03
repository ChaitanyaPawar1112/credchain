package com.credchain.modules.auth.application;

import java.time.Instant;

/** A token value plus when it expires. */
public record IssuedToken(String value, Instant expiresAt) {
}