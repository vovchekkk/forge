package com.forgeci.server.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forgeci.server.config.ForgeProperties;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtServiceTest {

    private ForgeProperties properties;
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        properties = new ForgeProperties();
        properties.getSecurity().getJwt().setSecret("a-very-long-test-secret-that-is-at-least-32-bytes-long!");
        properties.getSecurity().getJwt().setIssuer("forge-test");
        jwtService = new JwtService(properties);
    }

    private JwtService newService(ForgeProperties p) {
        return new JwtService(p);
    }

    @Test
    void issuedTokenParsesAndCarriesUserId() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.issueAccessToken(userId);
        Jwt parsed = jwtService.parseAccessToken(token);
        assertThat(parsed).isNotNull();
        assertThat(parsed.getSubject()).isEqualTo(userId.toString());
        assertThat(parsed.getClaimAsString(JwtService.TOKEN_TYPE_CLAIM)).isEqualTo(JwtService.ACCESS_TOKEN_TYPE);
    }

    @Test
    void parseRejectsGarbageToken() {
        assertThat(jwtService.parseAccessToken("not-a-jwt")).isNull();
    }

    @Test
    void parseRejectsTokenSignedWithDifferentKey() {
        ForgeProperties other = new ForgeProperties();
        other.getSecurity().getJwt().setSecret("another-secret-key-that-is-also-at-least-32-bytes!");
        other.getSecurity().getJwt().setIssuer("forge-test");
        JwtService otherService = newService(other);
        String token = otherService.issueAccessToken(UUID.randomUUID());
        assertThat(jwtService.parseAccessToken(token)).isNull();
    }

    @Test
    void parseRejectsWrongIssuer() {
        String token = jwtService.issueAccessToken(UUID.randomUUID());
        ForgeProperties otherIssuer = new ForgeProperties();
        otherIssuer.getSecurity().getJwt().setSecret("a-very-long-test-secret-that-is-at-least-32-bytes-long!");
        otherIssuer.getSecurity().getJwt().setIssuer("other-issuer");
        assertThat(newService(otherIssuer).parseAccessToken(token)).isNull();
    }

    @Test
    void rejectsShortSecret() {
        ForgeProperties bad = new ForgeProperties();
        bad.getSecurity().getJwt().setSecret("too-short");
        assertThatThrownBy(() -> newService(bad))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    void issuedTokenDoesNotExpireImmediately() {
        String token = jwtService.issueAccessToken(UUID.randomUUID());
        Jwt parsed = jwtService.parseAccessToken(token);
        assertThat(parsed).isNotNull();
        assertThat(parsed.getExpiresAt()).isAfter(Instant.now());
    }
}
