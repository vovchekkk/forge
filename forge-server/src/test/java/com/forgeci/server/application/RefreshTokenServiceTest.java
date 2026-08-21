package com.forgeci.server.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forgeci.server.config.ForgeProperties;
import com.forgeci.server.entity.RefreshTokenEntity;
import com.forgeci.server.entity.UserEntity;
import com.forgeci.server.repository.RefreshTokenRepository;
import com.forgeci.server.security.TokenHashing;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository tokenRepository;

    private ForgeProperties properties;
    private RefreshTokenService service;

    private final UserEntity user = new UserEntity("admin@test.com", "password");

    @BeforeEach
    void setUp() {
        properties = new ForgeProperties();
        service = new RefreshTokenService(tokenRepository, properties);
    }

    private RefreshTokenEntity entity(String raw, boolean revoked, java.util.UUID family) {
        RefreshTokenEntity e = new RefreshTokenEntity(user, TokenHashing.hash(raw), family,
                java.time.Instant.now().plus(Duration.ofDays(30)));
        e.setId(java.util.UUID.randomUUID());
        e.setRevoked(revoked);
        return e;
    }

    @Test
    void issueStoresHashNotRaw() {
        when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        String raw = service.issue(user);
        ArgumentCaptor<RefreshTokenEntity> captor = ArgumentCaptor.forClass(RefreshTokenEntity.class);
        verify(tokenRepository).save(captor.capture());
        RefreshTokenEntity stored = captor.getValue();
        assertThat(stored.getTokenHash()).isEqualTo(TokenHashing.hash(raw));
        assertThat(stored.getTokenHash()).isNotEqualTo(raw);
        assertThat(stored.getUser()).isEqualTo(user);
    }

    @Test
    void rotateReturnsNewRawTokenAndRevokesPresented() {
        java.util.UUID family = java.util.UUID.randomUUID();
        String oldRaw = "old-raw-token";
        RefreshTokenEntity oldEntity = entity(oldRaw, false, family);
        when(tokenRepository.findByTokenHash(TokenHashing.hash(oldRaw))).thenReturn(java.util.Optional.of(oldEntity));
        when(tokenRepository.findByFamilyAndRevokedFalse(family)).thenReturn(java.util.Optional.of(oldEntity));
        when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RefreshTokenService.Rotated rotated = service.rotate(oldRaw);

        assertThat(rotated.rawToken()).isNotEqualTo(oldRaw);
        assertThat(rotated.token().getFamily()).isEqualTo(family);
        assertThat(oldEntity.isRevoked()).isTrue();
    }

    @Test
    void rotateRejectsUnknownToken() {
        String unknown = "unknown-token";
        when(tokenRepository.findByTokenHash(TokenHashing.hash(unknown))).thenReturn(java.util.Optional.empty());
        assertThatThrownBy(() -> service.rotate(unknown))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void rotateRejectsExpiredTokenAndRevokesFamily() {
        java.util.UUID family = java.util.UUID.randomUUID();
        RefreshTokenEntity expired = new RefreshTokenEntity(user, TokenHashing.hash("expired-token"), family,
                java.time.Instant.now().minus(Duration.ofDays(1)));
        when(tokenRepository.findByTokenHash(expired.getTokenHash())).thenReturn(java.util.Optional.of(expired));
        when(tokenRepository.findAll()).thenReturn(List.of(expired));

        assertThatThrownBy(() -> service.rotate("expired-token"))
                .isInstanceOf(UnauthorizedException.class);
        assertThat(expired.isRevoked()).isTrue();
    }

    @Test
    void rotateDetectsReuseAndRevokesWholeFamily() {
        java.util.UUID family = java.util.UUID.randomUUID();
        RefreshTokenEntity presented = entity("presented-token", false, family);
        RefreshTokenEntity sibling = entity("sibling-token", false, family);
        when(tokenRepository.findByTokenHash(TokenHashing.hash("presented-token")))
                .thenReturn(java.util.Optional.of(presented));
        when(tokenRepository.findByFamilyAndRevokedFalse(family)).thenReturn(java.util.Optional.of(sibling));
        when(tokenRepository.findAll()).thenReturn(List.of(presented, sibling));

        assertThatThrownBy(() -> service.rotate("presented-token"))
                .isInstanceOf(UnauthorizedException.class);
        assertThat(presented.isRevoked()).isTrue();
        assertThat(sibling.isRevoked()).isTrue();
    }
}
