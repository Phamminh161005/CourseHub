package com.diagramio.api.share;

import com.diagramio.api.CallerResolver;
import com.diagramio.api.dto.ErrorResponse;
import com.diagramio.api.share.dto.CreateShareRequest;
import com.diagramio.api.share.dto.ShareResponse;
import com.diagramio.api.share.dto.UpdateShareRequest;
import com.diagramio.domain.access.ShareRole;
import com.diagramio.domain.share.DiagramShare;
import com.diagramio.config.OpenApiConfig;
import com.diagramio.domain.share.ShareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/diagrams/{id}/shares")
@Tag(name = "Diagram Sharing", description = "Quản lý liên kết và quyền chia sẻ sơ đồ (Dành cho Chủ sở hữu)")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@ApiResponse(responseCode = "401", description = "Chưa đăng nhập",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "Không phải chủ sở hữu sơ đồ",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
@ApiResponse(responseCode = "404", description = "Không tìm thấy sơ đồ hoặc liên kết chia sẻ",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
public class DiagramShareController {

    private final ShareService shareService;

    public DiagramShareController(ShareService shareService) {
        this.shareService = shareService;
    }

    @GetMapping
    @Operation(summary = "Danh sách liên kết chia sẻ của sơ đồ", description = "Chỉ chủ sở hữu (Owner) mới có quyền truy cập.")
    public ResponseEntity<List<ShareResponse>> getShares(
            @PathVariable("id") UUID diagramId,
            Authentication authentication) {

        List<DiagramShare> shares = shareService.getShares(diagramId, CallerResolver.resolve(authentication));
        List<ShareResponse> response = shares.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @PostMapping
    @Operation(summary = "Tạo liên kết chia sẻ mới", description = "Tạo liên kết chia sẻ với quyền view (chỉ xem) hoặc edit (chỉnh sửa). Chỉ chủ sở hữu mới có quyền.")
    @ApiResponse(responseCode = "201", description = "Đã tạo liên kết, kèm shareToken và shareUrl")
    @ApiResponse(responseCode = "400", description = "role không phải view/edit",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<ShareResponse> createShare(
            @PathVariable("id") UUID diagramId,
            @RequestBody CreateShareRequest request,
            Authentication authentication) {

        DiagramShare created = shareService.createShare(diagramId, ShareRole.from(request.role()),
                CallerResolver.resolve(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
    }

    @PatchMapping("/{share_id}")
    @Operation(summary = "Thay đổi mức độ quyền chia sẻ", description = "Đổi mức quyền (view <-> edit). Chỉ chủ sở hữu mới có quyền.")
    @ApiResponse(responseCode = "200", description = "Đã đổi quyền")
    @ApiResponse(responseCode = "400", description = "role không phải view/edit",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<ShareResponse> updateShare(
            @PathVariable("id") UUID diagramId,
            @PathVariable("share_id") UUID shareId,
            @RequestBody UpdateShareRequest request,
            Authentication authentication) {

        DiagramShare updated = shareService.updateShareRole(diagramId, shareId, ShareRole.from(request.role()),
                CallerResolver.resolve(authentication));
        return ResponseEntity.ok(toResponse(updated));
    }

    @DeleteMapping("/{share_id}")
    @Operation(summary = "Thu hồi liên kết chia sẻ", description = "Vô hiệu hóa liên kết chia sẻ ngay lập tức. Chỉ chủ sở hữu mới có quyền.")
    public ResponseEntity<Void> revokeShare(
            @PathVariable("id") UUID diagramId,
            @PathVariable("share_id") UUID shareId,
            Authentication authentication) {

        shareService.revokeShare(diagramId, shareId, CallerResolver.resolve(authentication));
        return ResponseEntity.noContent().build();
    }

    /** Đường dẫn chia sẻ theo địa chỉ server đang nhận request (localhost, Docker, máy chủ...). */
    private ShareResponse toResponse(DiagramShare share) {
        String shareUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/shares/{token}")
                .buildAndExpand(share.getShareToken())
                .toUriString();
        return ShareResponse.fromDomain(share, shareUrl);
    }
}
