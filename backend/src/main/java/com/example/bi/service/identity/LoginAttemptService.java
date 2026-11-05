package com.example.bi.service.identity;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

@Service
public class LoginAttemptService {

    public static final int MAX_ATTEMPTS = 5;
    public static final long LOCK_DURATION_SECONDS = 900; // 15 minutes

    public record AttemptInfo(int attempts, Instant firstAttemptTime, Instant lockedUntil) {}

    private final Cache<String, AttemptInfo> cache;
    private final boolean useForwardHeaders;

    public LoginAttemptService(@Value("${server.forward-headers-strategy:none}") String forwardHeadersStrategy) {
        this.useForwardHeaders = !"none".equalsIgnoreCase(forwardHeadersStrategy);
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(LOCK_DURATION_SECONDS))
                .build();
    }

    public String buildKey(String email, HttpServletRequest request) {
        String normalizedEmail = (email != null) ? email.trim().toLowerCase(Locale.ROOT) : "";
        String clientIp = extractClientIp(request);
        return normalizedEmail + "|" + clientIp;
    }

    public String extractClientIp(HttpServletRequest request) {
        if (useForwardHeaders) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
    }

    public boolean isBlocked(String key) {
        AttemptInfo info = cache.getIfPresent(key);
        if (info == null) {
            return false;
        }
        if (info.lockedUntil() != null) {
            if (Instant.now().isBefore(info.lockedUntil())) {
                return true;
            } else {
                cache.invalidate(key);
                return false;
            }
        }
        return false;
    }

    public long getRetryAfterSeconds(String key) {
        AttemptInfo info = cache.getIfPresent(key);
        if (info != null && info.lockedUntil() != null) {
            long remaining = Duration.between(Instant.now(), info.lockedUntil()).toSeconds();
            return Math.max(1, remaining);
        }
        return LOCK_DURATION_SECONDS;
    }

    public void loginFailed(String key) {
        AttemptInfo current = cache.getIfPresent(key);
        int attempts = (current != null) ? current.attempts() + 1 : 1;
        Instant firstTime = (current != null) ? current.firstAttemptTime() : Instant.now();
        Instant lockedUntil = null;
        if (attempts >= MAX_ATTEMPTS) {
            lockedUntil = Instant.now().plusSeconds(LOCK_DURATION_SECONDS);
        }
        cache.put(key, new AttemptInfo(attempts, firstTime, lockedUntil));
    }

    public void loginSucceeded(String key) {
        cache.invalidate(key);
    }
}

