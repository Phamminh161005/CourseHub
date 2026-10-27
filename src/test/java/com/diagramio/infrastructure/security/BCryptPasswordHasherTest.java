package com.diagramio.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BCryptPasswordHasherTest {

    private final BCryptPasswordHasher hasher = new BCryptPasswordHasher();

    @Test
    void hashIsNotThePlainPasswordAndMatches() {
        String hash = hasher.hash("secret123");

        assertThat(hash).isNotEqualTo("secret123").startsWith("$2a$10$");
        assertThat(hasher.matches("secret123", hash)).isTrue();
        assertThat(hasher.matches("wrong", hash)).isFalse();
    }

    @Test
    void samePasswordGivesDifferentHashes() {
        // BCrypt có muối ngẫu nhiên nên hai lần băm khác nhau, nhưng đều khớp
        assertThat(hasher.hash("secret123")).isNotEqualTo(hasher.hash("secret123"));
    }

    @Test
    void seededSampleUserHashMatchesDocumentedPassword() {
        // Hash trong V2__seed_sample_user.sql phải đăng nhập được bằng uet123456 như README ghi
        String seededHash = "$2a$10$jqmc./tQysLyXTNGg8M4CeIlAO5GffLskOQWPRLjPYfLidU3fVWEO";

        assertThat(hasher.matches("uet123456", seededHash)).isTrue();
    }
}
