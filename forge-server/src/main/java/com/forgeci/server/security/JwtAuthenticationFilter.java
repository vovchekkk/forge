package com.forgeci.server.security;

import com.forgeci.server.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates user principals from a {@code Bearer} access token issued by {@link JwtService}.
 * The principal is the user's UUID; user existence is re-validated so revoked/deleted users
 * lose access immediately even if the token is still within its validity window.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            Jwt jwt = jwtService.parseAccessToken(header.substring(7));
            if (jwt != null) {
                try {
                    UUID userId = UUID.fromString(jwt.getSubject());
                    userRepository.findById(userId).ifPresent(user -> {
                        if (user.isEnabled()) {
                            UsernamePasswordAuthenticationToken auth =
                                    new UsernamePasswordAuthenticationToken(userId, null, List.of());
                            SecurityContextHolder.getContext().setAuthentication(auth);
                        }
                    });
                } catch (IllegalArgumentException ignored) {
                    // malformed subject -> leave unauthenticated
                }
            }
        }
        chain.doFilter(request, response);
    }
}