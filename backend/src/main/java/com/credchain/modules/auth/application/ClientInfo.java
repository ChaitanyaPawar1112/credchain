package com.credchain.modules.auth.application;

/** Where a request came from; stored with refresh tokens for the "active sessions" view. */
public record ClientInfo(String ipAddress, String userAgent) {
}