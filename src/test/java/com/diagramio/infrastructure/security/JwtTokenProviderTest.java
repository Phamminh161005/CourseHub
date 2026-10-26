package com.diagramio.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.diagramio.domain.user.User;
import io.jsonwebtoken.security.WeakKeyException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    private static final String SECRET = "test-secret-that-is-at-least-32-bytes-long";
    private static final Instant NOW = Instant.parse("2026-10-26T10:00:00Z");
    private static final User USER = new User(UUID.randomUUID(), "uet", "uet@gmail.com", "hash");

    private final JwtTokenProvider provider = providerAt(NOW, SECRET);

    @Test
    void issuedTokenCanBeParsedBackToUserId() {
        String token = provider.issueAccessToken(USER);

        assertThat(provider.parseUserId(token)).contains(USER.id());
    }

    @Test
    void tokenIsStillValidJustBeforeExpiry() {
        String token = provider.issueAccessToken(USER);

        assertThat(providerAt(NOW.plus(Duration.ofMinutes(14)), SECRET).parseUserId(token)).contains(USER.id());
    }

    @Test
    void expiredTokenIsRejected() {
        String token = provider.issueAccessToken(USER);

        assertThat(providerAt(NOW.plus(Duration.ofMinutes(16)), SECRET).parseUserId(token)).isEmpty();
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        String token = providerAt(NOW, "another-secret-that-is-also-32-bytes-long").issueAccessToken(USER);

        assertThat(provider.parseUserId(token)).isEmpty();
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = provider.issueAccessToken(USER);
        // Đổi ký tự đầu của phần chữ ký (header.payload.signature)
        int signatureStart = token.lastIndexOf('.') + 1;
        char replacement = token.charAt(signatureStart) == 'A' ? 'B' : 'A';
        String tampered = token.substring(0, signatureStart) + replacement + token.substring(signatureStart + 1);

        assertThat(provider.parseUserId(tampered)).isEmpty();
    }

    @Test
    void garbageIsRejected() {
        assertThat(provider.parseUserId("not-a-jwt")).isEmpty();
        assertThat(provider.parseUserId("")).isEmpty();
    }

    @Test
    void shortSecretFailsFast() {
        assertThatThrownBy(() -> providerAt(NOW, "too-short"))
                .isInstanceOf(WeakKeyException.class);
    }

    private static JwtTokenProvider providerAt(Instant now, String secret) {
        return new JwtTokenProvider(new JwtProperties(secret, 15, 7), Clock.fixed(now, ZoneOffset.UTC));
    }
}
