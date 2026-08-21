package com.forgeci.server.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TokenHashingTest {

    @Test
    void hashIsDeterministic() {
        assertThat(TokenHashing.hash("same")).isEqualTo(TokenHashing.hash("same"));
    }

    @Test
    void hashDiffersAcrossInputs() {
        assertThat(TokenHashing.hash("a")).isNotEqualTo(TokenHashing.hash("b"));
    }

    @Test
    void hashIsSha256Hex() {
        assertThat(TokenHashing.hash("x")).matches("[0-9a-f]{64}");
    }

    @Test
    void generatedTokenIsLongAndUrlSafe() {
        String token = TokenHashing.generateToken();
        assertThat(token).matches("[A-Za-z0-9_-]{64}");
        assertThat(token.length()).isEqualTo(64);
    }

    @Test
    void generatedTokensAreUnique() {
        assertThat(TokenHashing.generateToken()).isNotEqualTo(TokenHashing.generateToken());
    }

    @Test
    void constantTimeEqualsAcceptsIdenticalValues() {
        assertThat(TokenHashing.constantTimeEquals("abc", "abc")).isTrue();
    }

    @Test
    void constantTimeEqualsRejectsDifferentValues() {
        assertThat(TokenHashing.constantTimeEquals("abc", "abd")).isFalse();
        assertThat(TokenHashing.constantTimeEquals("abc", "")).isFalse();
    }
}
