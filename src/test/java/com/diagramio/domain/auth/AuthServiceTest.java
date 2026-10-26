package com.diagramio.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.diagramio.domain.exception.ConflictException;
import com.diagramio.domain.exception.UnauthorizedException;
import com.diagramio.domain.user.User;
import com.diagramio.domain.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");
    private static final User USER = new User(UUID.randomUUID(), "uet", "uet@gmail.com", "hashed:secret",
            NOW.minus(Duration.ofDays(30)));

    private UserRepository userRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        PasswordHasher passwordHasher = new PasswordHasher() {
            @Override
            public String hash(String rawPassword) {
                return "hashed:" + rawPassword;
            }

            @Override
            public boolean matches(String rawPassword, String passwordHash) {
                return passwordHash.equals("hashed:" + rawPassword);
            }
        };
        TokenIssuer tokenIssuer = user -> "access-for-" + user.username();
        authService = new AuthService(userRepository, refreshTokenRepository, passwordHasher, tokenIssuer,
                Duration.ofDays(7), Clock.fixed(NOW, ZoneOffset.UTC));

        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---- register ----

    @Test
    void registerHashesPasswordAndNormalizesEmail() {
        User created = authService.register("minh", "  Minh@Gmail.COM ", "secret123");

        assertThat(created.id()).isNotNull();
        assertThat(created.email()).isEqualTo("minh@gmail.com");
        assertThat(created.passwordHash()).isEqualTo("hashed:secret123");
        assertThat(created.createdAt()).isEqualTo(NOW);
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByEmail("uet@gmail.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register("other", "uet@gmail.com", "secret123"))
                .isInstanceOf(ConflictException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerRejectsDuplicateUsername() {
        when(userRepository.existsByUsername("uet")).thenReturn(true);

        assertThatThrownBy(() -> authService.register("uet", "new@gmail.com", "secret123"))
                .isInstanceOf(ConflictException.class);
    }

    // ---- login ----

    @Test
    void loginReturnsTokensAndStoresOnlyHashOfRefreshToken() {
        when(userRepository.findByEmail("uet@gmail.com")).thenReturn(Optional.of(USER));

        AuthTokens tokens = authService.login("UET@gmail.com", "secret");

        assertThat(tokens.accessToken()).isEqualTo("access-for-uet");
        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(saved.capture());
        assertThat(saved.getValue().tokenHash())
                .isNotEqualTo(tokens.refreshToken())
                .isEqualTo(AuthService.hash(tokens.refreshToken()));
        assertThat(saved.getValue().userId()).isEqualTo(USER.id());
        assertThat(saved.getValue().expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(saved.getValue().revoked()).isFalse();
    }

    @Test
    void loginRejectsWrongPassword() {
        when(userRepository.findByEmail("uet@gmail.com")).thenReturn(Optional.of(USER));

        assertThatThrownBy(() -> authService.login("uet@gmail.com", "wrong"))
                .isInstanceOf(UnauthorizedException.class);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void loginRejectsUnknownEmail() {
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("nobody@gmail.com", "secret"))
                .isInstanceOf(UnauthorizedException.class);
    }

    // ---- refresh ----

    @Test
    void refreshRevokesOldTokenAndIssuesNewOne() {
        RefreshToken stored = storedToken("old-token", NOW.plusSeconds(60), false);
        when(userRepository.findById(USER.id())).thenReturn(Optional.of(USER));

        AuthTokens tokens = authService.refresh("old-token");

        assertThat(tokens.refreshToken()).isNotEqualTo("old-token");
        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, times(2)).save(saved.capture());
        List<RefreshToken> all = saved.getAllValues();
        assertThat(all.get(0).id()).isEqualTo(stored.id());
        assertThat(all.get(0).revoked()).isTrue();
        assertThat(all.get(1).tokenHash()).isEqualTo(AuthService.hash(tokens.refreshToken()));
        assertThat(all.get(1).revoked()).isFalse();
    }

    @Test
    void refreshRejectsRevokedToken() {
        storedToken("old-token", NOW.plusSeconds(60), true);

        assertThatThrownBy(() -> authService.refresh("old-token"))
                .isInstanceOf(UnauthorizedException.class);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void refreshRejectsExpiredToken() {
        storedToken("old-token", NOW.minusSeconds(1), false);

        assertThatThrownBy(() -> authService.refresh("old-token"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void refreshRejectsUnknownToken() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("made-up"))
                .isInstanceOf(UnauthorizedException.class);
    }

    // ---- logout ----

    @Test
    void logoutRevokesOwnToken() {
        RefreshToken stored = storedToken("my-token", NOW.plusSeconds(60), false);

        authService.logout(USER.id(), "my-token");

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(saved.capture());
        assertThat(saved.getValue().id()).isEqualTo(stored.id());
        assertThat(saved.getValue().revoked()).isTrue();
    }

    @Test
    void logoutIgnoresTokenOfAnotherUser() {
        storedToken("my-token", NOW.plusSeconds(60), false);

        authService.logout(UUID.randomUUID(), "my-token");

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void logoutWithUnknownTokenDoesNothing() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        authService.logout(USER.id(), "made-up");

        verify(refreshTokenRepository, never()).save(any());
    }

    private RefreshToken storedToken(String rawToken, Instant expiresAt, boolean revoked) {
        RefreshToken token = new RefreshToken(UUID.randomUUID(), USER.id(), AuthService.hash(rawToken),
                expiresAt, revoked);
        when(refreshTokenRepository.findByTokenHash(AuthService.hash(rawToken))).thenReturn(Optional.of(token));
        return token;
    }
}
