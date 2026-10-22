package com.diagramio.domain.auth;

/** Cặp token trả về sau khi đăng nhập hoặc refresh. refreshToken là token gốc, chỉ trả cho client một lần. */
public record AuthTokens(String accessToken, String refreshToken) {
}
