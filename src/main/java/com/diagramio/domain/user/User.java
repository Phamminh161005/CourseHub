package com.diagramio.domain.user;

import java.time.Instant;
import java.util.UUID;

/** Người dùng. passwordHash là bản băm, không bao giờ là mật khẩu gốc. */
public record User(UUID id, String username, String email, String passwordHash, Instant createdAt) {

    public User withPasswordHash(String newPasswordHash) {
        return new User(id, username, email, newPasswordHash, createdAt);
    }
}
