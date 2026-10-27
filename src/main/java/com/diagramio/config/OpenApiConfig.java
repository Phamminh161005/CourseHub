package com.diagramio.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Tài liệu OpenAPI. Mặc định mọi endpoint nhận JWT (bearerAuth) hoặc token chia sẻ (shareToken);
 * endpoint công khai đánh dấu @SecurityRequirements() để bỏ yêu cầu này.
 */
@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";
    public static final String SHARE_TOKEN = "shareToken";

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("DiagramIO API")
                        .version("v1")
                        .description("REST API cho dịch vụ vẽ sơ đồ UML/ERD trực tuyến. "
                                + "Đăng nhập lấy access token (bearerAuth), hoặc dùng token của link chia sẻ "
                                + "qua header X-Share-Token (shareToken) để xem/sửa sơ đồ được chia sẻ."))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT"))
                        .addSecuritySchemes(SHARE_TOKEN, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Share-Token")
                                .description("Token của link chia sẻ (view/edit), lấy từ POST /api/v1/diagrams/{id}/shares")))
                // Hai mục riêng = một trong hai là đủ
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
                .addSecurityItem(new SecurityRequirement().addList(SHARE_TOKEN));
    }
}
