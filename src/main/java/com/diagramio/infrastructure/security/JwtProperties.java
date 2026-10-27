package com.diagramio.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Cấu hình JWT đọc từ mục jwt.* trong application.yml. */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret, long accessTokenMinutes, long refreshTokenDays) {
}
