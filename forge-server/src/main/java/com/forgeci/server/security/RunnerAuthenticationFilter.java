package com.forgeci.server.security;

import com.forgeci.server.entity.RunnerEntity;
import com.forgeci.server.repository.RunnerRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;


@Component
public class RunnerAuthenticationFilter extends OncePerRequestFilter {

    public static final String RUNNER_TOKEN_HEADER = "X-Forge-Runner-Token";

    private final RunnerRepository runnerRepository;

    public RunnerAuthenticationFilter(RunnerRepository runnerRepository) {
        this.runnerRepository = runnerRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        UUID pathId = runnerPathId(request.getRequestURI());
        return pathId == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = request.getHeader(RUNNER_TOKEN_HEADER);
        UUID pathId = runnerPathId(request.getRequestURI());
        if (token != null && pathId != null) {
            RunnerEntity runner = runnerRepository.findByCredentialHash(TokenHashing.hash(token))
                    .filter(r -> !r.isRevoked())
                    .filter(r -> r.getId().equals(pathId))
                    .orElse(null);
            if (runner != null) {
                SecurityContextHolder.getContext()
                        .setAuthentication(new RunnerAuthenticationToken(runner, List.of()));
            }
        }
        chain.doFilter(request, response);
    }

    
    static UUID runnerPathId(String uri) {
        String prefix = "/api/runners/";
        if (!uri.startsWith(prefix)) {
            return null;
        }
        String rest = uri.substring(prefix.length());
        if (rest.isEmpty()) {
            return null;
        }
        String first = rest.split("/")[0];
        if ("register".equals(first)) {
            return null;
        }
        try {
            return UUID.fromString(first);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}