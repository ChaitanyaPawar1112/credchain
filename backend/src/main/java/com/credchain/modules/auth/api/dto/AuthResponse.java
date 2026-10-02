package com.credchain.modules.auth.api.dto;

import com.credchain.modules.auth.application.IssuedToken;
import com.credchain.modules.user.domain.User;

import java.time.Instant;

public record AuthResponse(
        String tokenType,
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt,
        UserSummary user
) {

    public static AuthResponse of(User user, IssuedToken access, IssuedToken refresh) {
        return new AuthResponse("Bearer",
                access.value(), access.expiresAt(),
                refresh.value(), refresh.expiresAt(),
                UserSummary.from(user));
    }
}