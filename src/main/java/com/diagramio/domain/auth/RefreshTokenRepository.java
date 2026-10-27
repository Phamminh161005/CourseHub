package com.diagramio.domain.auth;

import java.util.Optional;

/** Cổng truy cập dữ liệu refresh token; tầng infrastructure cài đặt bằng JPA. */
public interface RefreshTokenRepository {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    RefreshToken save(RefreshToken refreshToken);
}
