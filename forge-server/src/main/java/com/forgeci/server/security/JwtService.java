package com.forgeci.server.security;

import com.forgeci.server.config.ForgeProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.MappedJwtClaimSetConverter;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/**
 * Issues and validates HMAC HS256 access tokens. Requires a configured secret of at
 * least 32 bytes; if none is configured a random ephemeral secret is generated (dev only).
 */
@Component
public class JwtService {

    public static final String TOKEN_TYPE_CLAIM = "type";
    public static final String ACCESS_TOKEN_TYPE = "access";

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final NimbusJwtDecoder decoder;
    private final ForgeProperties properties;
    private final SecretKey key;

    public JwtService(ForgeProperties properties) {
        this.properties = properties;
        String secret = properties.getSecurity().getJwt().getSecret();
        if (secret == null || secret.isBlank()) {
            byte[] generated = new byte[32];
            new java.security.SecureRandom().nextBytes(generated);
            secret = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(generated);
            log.warn("forge.security.jwt.secret is not configured; generated an ephemeral signing secret. "
                    + "Set FORGE_JWT_SECRET in production.");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("forge.security.jwt.secret must be at least 32 bytes for HS256");
        }
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        // Default verifier treats "iss" as a URL; our issuer is a simple name, so use an
        // exact-match verifier that also enforces expiry.
        JWTClaimsSet requiredClaims = new JWTClaimsSet.Builder()
                .issuer(properties.getSecurity().getJwt().getIssuer())
                .build();
        // Spring's default claim converter turns "iss" into a URI; our issuer is a plain
        // name, so keep it a String and enforce exact-match issuer + expiry ourselves.
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .jwtProcessorCustomizer(c -> c.setJWTClaimsSetVerifier(
                        new DefaultJWTClaimsVerifier<>(requiredClaims, Set.of())))
                .build();
        decoder.setClaimSetConverter(
                MappedJwtClaimSetConverter.withDefaults(Map.of("iss", (Converter<Object, ?>) value -> value)));
        this.decoder = decoder;
    }

    public String issueAccessToken(UUID userId) {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(properties.getSecurity().getJwt().getIssuer())
                .subject(userId.toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(properties.getSecurity().getJwt().getAccessTokenTtl())))
                .claim(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE)
                .jwtID(UUID.randomUUID().toString())
                .build();
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.HS256)
                .type(JOSEObjectType.JWT)
                .keyID("forge-jwt")
                .build();
        SignedJWT signedJWT = new SignedJWT(header, claims);
        try {
            signedJWT.sign(new MACSigner(key));
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to sign access token", e);
        }
        return signedJWT.serialize();
    }

    /** Returns the validated access token, or null when absent, invalid, expired or of wrong type. */
    public Jwt parseAccessToken(String token) {
        try {
            Jwt jwt = decoder.decode(token);
            if (!ACCESS_TOKEN_TYPE.equals(jwt.getClaimAsString(TOKEN_TYPE_CLAIM))) {
                return null;
            }
            if (!properties.getSecurity().getJwt().getIssuer().equals(jwt.getClaimAsString("iss"))) {
                return null;
            }
            if (jwt.getExpiresAt() == null || jwt.getExpiresAt().isBefore(Instant.now())) {
                return null;
            }
            return jwt;
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}