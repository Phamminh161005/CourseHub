package com.diagramio.domain.auth;

import com.diagramio.domain.exception.ConflictException;
import com.diagramio.domain.exception.UnauthorizedException;
import com.diagramio.domain.user.User;
import com.diagramio.domain.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

/** Nghiệp vụ xác thực: đăng ký, đăng nhập, refresh (rotation), đăng xuất. */
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordHasher passwordHasher;
    private final TokenIssuer tokenIssuer;
    private final Duration refreshTokenTtl;
    private final Clock clock;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordHasher passwordHasher,
                       TokenIssuer tokenIssuer,
                       Duration refreshTokenTtl,
                       Clock clock) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordHasher = passwordHasher;
        this.tokenIssuer = tokenIssuer;
        this.refreshTokenTtl = refreshTokenTtl;
        this.clock = clock;
    }

    public User register(String username, String email, String rawPassword) {
        String normalizedEmail = normalizeEmail(email);
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ConflictException("Email is already registered");
        }
        if (userRepository.existsByUsername(username)) {
            throw new ConflictException("Username is already taken");
        }
        User user = new User(UUID.randomUUID(), username, normalizedEmail, passwordHasher.hash(rawPassword));
        return userRepository.save(user);
    }

    public AuthTokens login(String email, String rawPassword) {
        // Cùng một thông báo cho sai email và sai mật khẩu, để không lộ email nào đã đăng ký
        User user = userRepository.findByEmail(normalizeEmail(email))
                .filter(u -> passwordHasher.matches(rawPassword, u.passwordHash()))
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));
        return issueTokens(user);
    }

    /** Đổi refresh token lấy cặp token mới; token cũ bị thu hồi ngay (rotation). */
    public AuthTokens refresh(String rawRefreshToken) {
        RefreshToken current = refreshTokenRepository.findByTokenHash(hash(rawRefreshToken))
                .filter(t -> t.isUsable(clock.instant()))
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired refresh token"));
        refreshTokenRepository.save(current.revoke());
        User user = userRepository.findById(current.userId())
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired refresh token"));
        return issueTokens(user);
    }

    /** Thu hồi refresh token được gửi lên. Token không tồn tại hoặc đã thu hồi thì bỏ qua. */
    public void logout(String rawRefreshToken) {
        refreshTokenRepository.findByTokenHash(hash(rawRefreshToken))
                .filter(t -> !t.revoked())
                .ifPresent(t -> refreshTokenRepository.save(t.revoke()));
    }

    private AuthTokens issueTokens(User user) {
        String rawRefreshToken = generateRefreshToken();
        Instant expiresAt = clock.instant().plus(refreshTokenTtl);
        refreshTokenRepository.save(
                new RefreshToken(UUID.randomUUID(), user.id(), hash(rawRefreshToken), expiresAt, false));
        return new AuthTokens(tokenIssuer.issueAccessToken(user), rawRefreshToken);
    }

    private static String generateRefreshToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
