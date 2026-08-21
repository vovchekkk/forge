package com.forgeci.server.application;

import com.forgeci.server.config.ForgeProperties;
import com.forgeci.server.entity.RefreshTokenEntity;
import com.forgeci.server.entity.UserEntity;
import com.forgeci.server.repository.RefreshTokenRepository;
import com.forgeci.server.security.TokenHashing;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opaque refresh tokens: random 48-byte values stored only as SHA-256 hashes,
 * grouped into families for rotation and reuse detection. Presenting a token whose
 * family was already rotated revokes the whole family (credential theft signal).
 */
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository tokenRepository;
    private final ForgeProperties properties;

    public RefreshTokenService(RefreshTokenRepository tokenRepository, ForgeProperties properties) {
        this.tokenRepository = tokenRepository;
        this.properties = properties;
    }

    /** Issue a fresh refresh token for a user, as the start of a new family. */
    @Transactional
    public String issue(UserEntity user) {
        String raw = TokenHashing.generateToken();
        RefreshTokenEntity entity = new RefreshTokenEntity(
                user,
                TokenHashing.hash(raw),
                UUID.randomUUID(),
                Instant.now().plus(properties.getSecurity().getJwt().getRefreshTokenTtl()));
        tokenRepository.save(entity);
        return raw;
    }

    /**
     * Rotate a refresh token. Returns a new raw token bound to a new record in the same
     * family, revoking the presented one. On detected reuse (family already rotated),
     * revokes the entire family and rejects.
     */
    @Transactional
    public Rotated rotate(String rawToken) {
        String hash = TokenHashing.hash(rawToken);
        RefreshTokenEntity presented = tokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (presented.isRevoked() || presented.getExpiresAt().isBefore(Instant.now())) {
            revokeFamily(presented.getFamily());
            throw new UnauthorizedException("Invalid refresh token");
        }

        Optional<RefreshTokenEntity> activeInFamily = tokenRepository.findByFamilyAndRevokedFalse(presented.getFamily());
        if (activeInFamily.isPresent() && !activeInFamily.get().getId().equals(presented.getId())) {
            // A different active token exists in this family: the presented token was reused.
            revokeFamily(presented.getFamily());
            throw new UnauthorizedException("Invalid refresh token");
        }

        String raw = TokenHashing.generateToken();
        RefreshTokenEntity next = new RefreshTokenEntity(
                presented.getUser(),
                TokenHashing.hash(raw),
                presented.getFamily(),
                Instant.now().plus(properties.getSecurity().getJwt().getRefreshTokenTtl()));
        tokenRepository.save(next);

        presented.setRevoked(true);
        presented.setReplacedBy(next.getId());
        tokenRepository.save(presented);

        return new Rotated(raw, next, presented.getUser());
    }

    /** Revoke a token and all its siblings in the family. */
    @Transactional
    public void revokeFamily(UUID family) {
        for (RefreshTokenEntity token : tokenRepository.findAll()) {
            if (token.getFamily().equals(family) && !token.isRevoked()) {
                token.setRevoked(true);
                tokenRepository.save(token);
            }
        }
    }

    public record Rotated(String rawToken, RefreshTokenEntity token, UserEntity user) {}
}