package com.diagramio.api;

import com.diagramio.domain.DatabaseHealth;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Trạng thái dịch vụ và kết nối CSDL: 200 nếu ổn, 503 nếu mất CSDL (Docker healthcheck dựa vào mã này). */
@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "System", description = "Trạng thái dịch vụ")
@SecurityRequirements
public class HealthController {

    private final DatabaseHealth databaseHealth;

    public HealthController(DatabaseHealth databaseHealth) {
        this.databaseHealth = databaseHealth;
    }

    @GetMapping
    @Operation(summary = "Kiểm tra dịch vụ và kết nối CSDL")
    @ApiResponse(responseCode = "200", description = "Dịch vụ và CSDL hoạt động")
    @ApiResponse(responseCode = "503", description = "Mất kết nối CSDL")
    public ResponseEntity<Map<String, String>> health() {
        boolean databaseUp = databaseHealth.isUp();
        String status = databaseUp ? "UP" : "DOWN";
        return ResponseEntity.status(databaseUp ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("status", status, "database", status));
    }
}
