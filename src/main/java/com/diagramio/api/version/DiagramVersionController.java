package com.diagramio.api.version;

import com.diagramio.api.CallerResolver;
import com.diagramio.api.dto.ErrorResponse;
import com.diagramio.domain.version.DiagramVersion;
import com.diagramio.domain.version.DiagramVersionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/diagrams/{diagramId}/versions")
@Tag(name = "Diagram versions", description = "Lịch sử phiên bản (snapshot bất biến) và khôi phục sơ đồ")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "401", description = "Chưa đăng nhập",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "Không đủ quyền với sơ đồ",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
@ApiResponse(responseCode = "404", description = "Không tìm thấy sơ đồ hoặc phiên bản",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
public class DiagramVersionController {

    private final DiagramVersionService versionService;

    public DiagramVersionController(DiagramVersionService versionService) {
        this.versionService = versionService;
    }

    @GetMapping
    @Operation(summary = "Danh sách phiên bản đã lưu (mới nhất trước)",
            description = "Owner hoặc người có link chia sẻ view/edit.")
    @ApiResponse(responseCode = "200", description = "Danh sách phiên bản")
    public List<DiagramVersionSummaryResponse> list(@PathVariable UUID diagramId, Authentication authentication) {
        return versionService.listVersions(diagramId, CallerResolver.resolve(authentication)).stream()
                .map(DiagramVersionSummaryResponse::from)
                .toList();
    }

    @PostMapping
    @Operation(summary = "Lưu snapshot dữ liệu hiện tại kèm ghi chú", description = "Owner hoặc editor.")
    @ApiResponse(responseCode = "201", description = "Đã tạo phiên bản")
    @ApiResponse(responseCode = "400", description = "Ghi chú quá 255 ký tự",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Sơ đồ đã bị sửa sau version client gửi lên, "
            + "hoặc có phiên bản khác được lưu cùng lúc; tải lại rồi thử lại",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<DiagramVersionResponse> create(@PathVariable UUID diagramId,
                                                         @Valid @RequestBody(required = false)
                                                         CreateVersionRequest request,
                                                         Authentication authentication) {
        String note = request == null ? null : request.note();
        Integer expectedVersion = request == null ? null : request.version();
        DiagramVersion version = versionService.createVersion(diagramId, note, expectedVersion,
                CallerResolver.resolve(authentication));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{versionId}")
                .buildAndExpand(version.id())
                .toUri();
        return ResponseEntity.created(location).body(DiagramVersionResponse.from(version));
    }

    @GetMapping("/{versionId}")
    @Operation(summary = "Chi tiết cấu trúc JSON của một phiên bản",
            description = "Owner hoặc người có link chia sẻ view/edit.")
    @ApiResponse(responseCode = "200", description = "Chi tiết phiên bản")
    public DiagramVersionResponse get(@PathVariable UUID diagramId, @PathVariable UUID versionId,
                                      Authentication authentication) {
        return DiagramVersionResponse.from(
                versionService.getVersion(diagramId, versionId, CallerResolver.resolve(authentication)));
    }

    @PostMapping("/{versionId}/restore")
    @Operation(summary = "Khôi phục sơ đồ về phiên bản đã chọn",
            description = "Owner hoặc editor. Trạng thái hiện tại được tự động lưu thành một snapshot sao lưu "
                    + "trước khi ghi đè; version của sơ đồ tăng thêm 1.")
    @ApiResponse(responseCode = "200", description = "Đã khôi phục")
    @ApiResponse(responseCode = "409", description = "Sơ đồ vừa bị người khác cập nhật, thử lại",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public RestoreVersionResponse restore(@PathVariable UUID diagramId, @PathVariable UUID versionId,
                                          Authentication authentication) {
        return RestoreVersionResponse.from(
                versionService.restore(diagramId, versionId, CallerResolver.resolve(authentication)));
    }
}
