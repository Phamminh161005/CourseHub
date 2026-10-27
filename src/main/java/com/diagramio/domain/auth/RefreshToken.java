package com.diagramio.domain.auth;

import java.time.Instant;
import java.util.UUID;

/** Refresh token đã lưu. Chỉ giữ bản băm SHA-256 (tokenHash), không giữ token gốc. */
public record RefreshToken(UUID id, UUID userId, String tokenHash, Instant expiresAt, boolean revoked) {

    /** Còn dùng được: chưa bị thu hồi và chưa hết hạn. */
    public boolean isUsable(Instant now) {
        return !revoked && now.isBefore(expiresAt);
    }

    public RefreshToken revoke() {
        return new RefreshToken(id, userId, tokenHash, expiresAt, true);
    }
}
