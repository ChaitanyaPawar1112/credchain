package com.credchain.modules.verification.ratelimit;

import com.credchain.common.exception.BusinessException;
import com.credchain.common.exception.ErrorCode;
import com.credchain.modules.verification.config.PublicRateLimitProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.time.Clock;
import java.util.Set;

/**
 * Stops one client from hammering what anyone can call without logging in: the public verification API (scraping
 * hashes, flooding PDF uploads) and the sign-in forms (guessing passwords across many accounts, spam sign-ups).
 * Over the limit: 429 with code RATE_LIMITED and a Retry-After header.
 */
@Slf4j
@Component
public class PublicRateLimitFilter extends OncePerRequestFilter {

    static final String PUBLIC_PREFIX = "/api/v1/public/";
    /** POST only; the account lockout after wrong passwords still applies on top of this. */
    static final Set<String> SIGN_IN_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/institutions/applications");

    private final PublicRateLimitProperties properties;
    private final HandlerExceptionResolver resolver;
    private final FixedWindowRateLimiter limiter;

    public PublicRateLimitFilter(PublicRateLimitProperties properties,
                                 @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver,
                                 Clock clock) {
        this.properties = properties;
        this.resolver = resolver;
        this.limiter = new FixedWindowRateLimiter(clock);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.enabled() || (!isPublic(request) && !isSignIn(request));
    }

    private static boolean isPublic(HttpServletRequest request) {
        return request.getRequestURI().startsWith(PUBLIC_PREFIX);
    }

    private static boolean isSignIn(HttpServletRequest request) {
        return HttpMethod.POST.matches(request.getMethod()) && SIGN_IN_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String ip = clientIp(request);
        boolean signIn = isSignIn(request);
        FixedWindowRateLimiter.Decision decision;
        if (signIn) {
            decision = limiter.tryAcquire("sign-in:" + ip, properties.signInsPerMinute());
        } else if (HttpMethod.POST.matches(request.getMethod())) {
            decision = limiter.tryAcquire("upload:" + ip, properties.uploadsPerMinute());
        } else {
            decision = limiter.tryAcquire("lookup:" + ip, properties.lookupsPerMinute());
        }
        if (decision.allowed()) {
            chain.doFilter(request, response);
            return;
        }
        log.warn("Rate limit hit on {} {} by {}", request.getMethod(), request.getRequestURI(), ip);
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(decision.retryAfterSeconds()));
        String what = signIn ? "attempts" : "verification requests";
        resolver.resolveException(request, response, null, new BusinessException(ErrorCode.RATE_LIMITED,
                "Too many " + what + ". Please try again in " + decision.retryAfterSeconds() + " seconds."));
    }

    /** First address in X-Forwarded-For when we sit behind our own proxy, otherwise the connection's address. */
    String clientIp(HttpServletRequest request) {
        if (properties.trustForwardedFor()) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
