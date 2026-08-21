package com.forgeci.server.application;

import com.forgeci.server.config.ForgeProperties;
import com.forgeci.server.entity.UserEntity;
import com.forgeci.server.repository.UserRepository;
import com.forgeci.server.security.JwtService;
import com.forgeci.server.web.dto.AuthResponse;
import com.forgeci.server.web.dto.LoginRequest;
import com.forgeci.server.web.dto.RegisterRequest;
import com.forgeci.server.web.dto.RefreshRequest;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final LoginAttemptService loginAttemptService;
    private final AuditService auditService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       RefreshTokenService refreshTokenService,
                       LoginAttemptService loginAttemptService,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.loginAttemptService = loginAttemptService;
        this.auditService = auditService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists");
        }
        UserEntity user = new UserEntity(email, passwordEncoder.encode(request.password()));
        userRepository.save(user);
        auditService.recordUserEvent("USER_REGISTERED", user.getId(), "email=" + email);
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();
        if (loginAttemptService.isBlocked(email)) {
            auditService.recordUserEvent("LOGIN_THROTTLED", null, "email=" + email);
            throw new RateLimitException("Too many failed attempts; try again later");
        }
        UserEntity user = userRepository.findByEmail(email).orElse(null);
        boolean passwordOk = user != null
                && user.isEnabled()
                && passwordEncoder.matches(request.password(), user.getPasswordHash());
        if (user == null || !passwordOk) {
            loginAttemptService.recordFailure(email);
            auditService.recordUserEvent("LOGIN_FAILED", user == null ? null : user.getId(), "email=" + email);
            throw new UnauthorizedException("Invalid credentials");
        }
        loginAttemptService.reset(email);
        auditService.recordUserEvent("LOGIN_OK", user.getId(), "email=" + email);
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        RefreshTokenService.Rotated rotated = refreshTokenService.rotate(request.refreshToken());
        UserEntity user = rotated.user();
        if (!user.isEnabled()) {
            throw new UnauthorizedException("Invalid credentials");
        }
        auditService.recordUserEvent("TOKEN_REFRESHED", user.getId(), null);
        return issueTokens(user, rotated.rawToken());
    }

    /** Log out by revoking the presented refresh token family. */
    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        RefreshTokenService.Rotated rotated = refreshTokenService.rotate(refreshToken);
        refreshTokenService.revokeFamily(rotated.token().getFamily());
        auditService.recordUserEvent("LOGOUT", rotated.user().getId(), null);
    }

    private AuthResponse issueTokens(UserEntity user) {
        return issueTokens(user, refreshTokenService.issue(user));
    }

    private AuthResponse issueTokens(UserEntity user, String refreshToken) {
        String accessToken = jwtService.issueAccessToken(user.getId());
        return new AuthResponse(user.getId(), user.getEmail(), accessToken, refreshToken);
    }
}