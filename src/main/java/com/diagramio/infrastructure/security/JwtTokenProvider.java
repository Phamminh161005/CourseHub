package com.diagramio.infrastructure.security;

import com.diagramio.domain.auth.TokenIssuer;
import com.diagramio.domain.user.User;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * Tạo và kiểm tra access token (JWT ký HMAC; jjwt chọn HS256/384/512 theo độ dài khóa).
 * subject = id người dùng.
 */
@Component
public class JwtTokenProvider implements TokenIssuer {

    private final SecretKey key;
    private final Duration accessTokenTtl;
    private final Clock clock;

    public JwtTokenProvider(JwtProperties properties, Clock clock) {
        // Khóa ngắn hơn 32 byte bị jjwt từ chối ngay khi khởi động (WeakKeyException)
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtl = Duration.ofMinutes(properties.accessTokenMinutes());
        this.clock = clock;
    }

    @Override
    public String issueAccessToken(User user) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(user.id().toString())
                .claim("username", user.username())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtl)))
                .signWith(key)
                .compact();
    }

    /** Id người dùng nếu token hợp lệ (đúng chữ ký, chưa hết hạn); rỗng trong mọi trường hợp khác. */
    public Optional<UUID> parseUserId(String token) {
        try {
            String subject = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
            return Optional.of(UUID.fromString(subject));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
