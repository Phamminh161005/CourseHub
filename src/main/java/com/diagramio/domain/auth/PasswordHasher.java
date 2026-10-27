package com.diagramio.domain.auth;

/** Băm và kiểm tra mật khẩu; cài đặt bằng BCrypt ở tầng ngoài. */
public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String passwordHash);
}
