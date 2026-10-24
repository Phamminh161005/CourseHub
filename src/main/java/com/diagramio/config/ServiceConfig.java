package com.diagramio.config;

import com.diagramio.domain.repository.DiagramRepository;
import com.diagramio.domain.repository.DiagramShareRepository;
import com.diagramio.domain.service.ShareService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Đăng ký các Bean cho Domain Services.
 * Tuân thủ Clean Architecture: Domain không dùng @Service hay annotation của Spring,
 * việc khởi tạo service được thực hiện tập trung tại tầng Config.
 */
@Configuration
public class ServiceConfig {

    @Bean
    public ShareService shareService(DiagramShareRepository shareRepository, DiagramRepository diagramRepository) {
        return new ShareService(shareRepository, diagramRepository);
    }
}
