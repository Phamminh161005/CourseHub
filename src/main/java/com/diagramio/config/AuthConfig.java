package com.diagramio.config;

import com.diagramio.infrastructure.security.JwtProperties;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Lắp ghép phần xác thực. */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class AuthConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
