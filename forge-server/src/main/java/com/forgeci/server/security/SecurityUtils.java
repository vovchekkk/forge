package com.forgeci.server.security;

import com.forgeci.server.application.UnauthorizedException;
import com.forgeci.server.entity.RunnerEntity;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;


public final class SecurityUtils {

    private SecurityUtils() {}

    
    public static UUID requireUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof UUID userId) {
            return userId;
        }
        throw new UnauthorizedException("Authentication required");
    }

    
    public static RunnerEntity requireRunner() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof RunnerEntity runner) {
            return runner;
        }
        throw new UnauthorizedException("Runner authentication required");
    }
}