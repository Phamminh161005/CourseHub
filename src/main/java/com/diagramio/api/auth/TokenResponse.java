package com.diagramio.api.auth;

import com.diagramio.domain.auth.AuthTokens;

public record TokenResponse(String accessToken, String refreshToken, String tokenType) {

    static TokenResponse from(AuthTokens tokens) {
        return new TokenResponse(tokens.accessToken(), tokens.refreshToken(), "Bearer");
    }
}
