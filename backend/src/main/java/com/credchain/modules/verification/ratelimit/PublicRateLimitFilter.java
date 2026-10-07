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

/**
 * Stops one client from hammering the public verification API (scraping hashes, flooding PDF uploads).
 * Over the limit: 429 with code RATE_LIMITED and a Retry-After header.
 */
@Slf4j
@Component
public class PublicRateLimitFilter extends OncePerRequestFilter {

    static final String PUBLIC_PREFIX = "/api/v1/public/";

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
        return !properties.enabled() || !request.getRequestURI().startsWith(PUBLIC_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean upload = HttpMethod.POST.matches(request.getMethod());
        String ip = clientIp(request);
        FixedWindowRateLimiter.Decision decision = upload
                ? limiter.tryAcquire("upload:" + ip, properties.uploadsPerMinute())
                : limiter.tryAcquire("lookup:" + ip, properties.lookupsPerMinute());
        if (decision.allowed()) {
            chain.doFilter(request, response);
            return;
        }
        log.warn("Rate limit hit on {} {} by {}", request.getMethod(), request.getRequestURI(), ip);
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(decision.retryAfterSeconds()));
        resolver.resolveException(request, response, null, new BusinessException(ErrorCode.RATE_LIMITED,
                "Too many verification requests. Please try again in " + decision.retryAfterSeconds() + " seconds."));
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
