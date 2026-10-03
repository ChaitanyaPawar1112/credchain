package com.credchain.modules.auth.application;

import com.credchain.modules.user.domain.User;

/** Result of a successful refresh: who the user is + their new refresh token. */
public record RotationResult(User user, IssuedToken refreshToken) {
}