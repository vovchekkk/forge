package com.forgeci.server.security;

import com.forgeci.server.application.UnauthorizedException;
import com.forgeci.server.entity.RunnerEntity;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Helpers for controllers/services to resolve the current principal.
 * Controllers never trust an owner id from the request body — it always comes from here.
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    /** The authenticated user's id, or 401 when the caller is not a user. */
    public static UUID requireUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof UUID userId) {
            return userId;
        }
        throw new UnauthorizedException("Authentication required");
    }

    /** The authenticated runner, or 401 when the caller is not a runner. */
    public static RunnerEntity requireRunner() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof RunnerEntity runner) {
            return runner;
        }
        throw new UnauthorizedException("Runner authentication required");
    }
}