package com.diagramio.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.diagramio.domain.auth.RefreshToken;
import com.diagramio.domain.user.User;
import com.diagramio.infrastructure.auth.RefreshTokenRepositoryAdapter;
import com.diagramio.infrastructure.user.UserRepositoryAdapter;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Chạy với Postgres thật (Testcontainers); Flyway tạo bảng như khi chạy app.
 * Máy không có Docker thì test này tự bỏ qua.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Import({UserRepositoryAdapter.class, RefreshTokenRepositoryAdapter.class})
class RepositoryAdaptersTest {

    private static final UUID SAMPLE_USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private UserRepositoryAdapter users;

    @Autowired
    private RefreshTokenRepositoryAdapter refreshTokens;

    @Test
    void findsSeededSampleUser() {
        assertThat(users.findByEmail("uet@gmail.com"))
                .hasValueSatisfying(u -> {
                    assertThat(u.id()).isEqualTo(SAMPLE_USER_ID);
                    assertThat(u.username()).isEqualTo("uet");
                    assertThat(u.createdAt()).isNotNull(); // DB tự điền khi seed
                });
        assertThat(users.findById(SAMPLE_USER_ID)).isPresent();
        assertThat(users.existsByEmail("uet@gmail.com")).isTrue();
        assertThat(users.existsByUsername("uet")).isTrue();
        assertThat(users.findByEmail("nobody@gmail.com")).isEmpty();
    }

    @Test
    void savesNewUser() {
        Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        User saved = users.save(new User(UUID.randomUUID(), "minh", "minh@gmail.com", "hash", createdAt));

        assertThat(users.findByEmail("minh@gmail.com")).contains(saved);
        assertThat(users.existsByUsername("minh")).isTrue();
    }

    @Test
    void updatesPasswordHashOfExistingUser() {
        User sample = users.findById(SAMPLE_USER_ID).orElseThrow();

        users.save(sample.withPasswordHash("new-hash"));

        assertThat(users.findById(SAMPLE_USER_ID))
                .hasValueSatisfying(u -> {
                    assertThat(u.passwordHash()).isEqualTo("new-hash");
                    assertThat(u.createdAt()).isEqualTo(sample.createdAt());
                });
    }

    @Test
    void savesFindsAndRevokesRefreshToken() {
        Instant expiresAt = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS);
        RefreshToken token = new RefreshToken(UUID.randomUUID(), SAMPLE_USER_ID, "a".repeat(64), expiresAt, false);

        refreshTokens.save(token);
        assertThat(refreshTokens.findByTokenHash("a".repeat(64))).contains(token);

        refreshTokens.save(token.revoke());
        assertThat(refreshTokens.findByTokenHash("a".repeat(64)))
                .hasValueSatisfying(t -> assertThat(t.revoked()).isTrue());
        assertThat(refreshTokens.findByTokenHash("b".repeat(64))).isEmpty();
    }
}
