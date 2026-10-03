package com.credchain.modules.auth.application;

import com.credchain.common.security.JwtConfig;
import com.credchain.common.security.SecurityProperties;
import com.credchain.modules.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Creates signed JWT access tokens. */
@Service
@RequiredArgsConstructor
public class AccessTokenService {

    private final JwtEncoder jwtEncoder;
    private final SecurityProperties properties;
    private final Clock clock;

    public IssuedToken issue(User user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.jwt().accessTokenTtl());

        JwtClaimsSet claims = JwtClaimsSet.builder()

                .issuer(properties.jwt().issuer())
                .subject(user.getId().toString())          // "sub" = user id
                .issuedAt(now)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())           // "jti" = unique token id
                .claim("email", user.getEmail())
                .claim(JwtConfig.ROLES_CLAIM, List.of(user.getRole().name()))
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, expiresAt);
    }
}