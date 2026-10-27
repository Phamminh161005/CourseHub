package com.diagramio.config;

import com.diagramio.domain.TransactionRunner;
import com.diagramio.domain.auth.AuthService;
import com.diagramio.domain.auth.PasswordHasher;
import com.diagramio.domain.auth.RefreshTokenRepository;
import com.diagramio.domain.auth.TokenIssuer;
import com.diagramio.domain.user.UserRepository;
import com.diagramio.domain.user.UserService;
import com.diagramio.infrastructure.security.JwtProperties;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Lắp ghép phần xác thực và tài khoản người dùng. */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class AuthConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public AuthService authService(UserRepository userRepository,
                                   RefreshTokenRepository refreshTokenRepository,
                                   PasswordHasher passwordHasher,
                                   TokenIssuer tokenIssuer,
                                   TransactionRunner transactionRunner,
                                   JwtProperties jwtProperties,
                                   Clock clock) {
        return new AuthService(userRepository, refreshTokenRepository, passwordHasher, tokenIssuer,
                transactionRunner, Duration.ofDays(jwtProperties.refreshTokenDays()), clock);
    }

    @Bean
    public UserService userService(UserRepository userRepository, PasswordHasher passwordHasher) {
        return new UserService(userRepository, passwordHasher);
    }
}
