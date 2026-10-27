package com.diagramio.api.share;

import com.diagramio.domain.share.DiagramShare;
import com.diagramio.domain.share.ShareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/diagrams/{id}/shares")
@Tag(name = "Diagram Sharing", description = "Quản lý liên kết và quyền chia sẻ sơ đồ (Dành cho Chủ sở hữu)")
public class DiagramShareController {

    private static final UUID DEFAULT_SAMPLE_USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final ShareService shareService;

    public DiagramShareController(ShareService shareService) {
        this.shareService = shareService;
    }

    @GetMapping
    @Operation(summary = "Danh sách liên kết chia sẻ của sơ đồ", description = "Chỉ chủ sở hữu (Owner) mới có quyền truy cập.")
    public ResponseEntity<List<ShareResponse>> getShares(
            @PathVariable("id") UUID diagramId,
            @RequestHeader(value = "X-User-Id", required = false) UUID headerUserId,
            Authentication authentication) {

        UUID currentUserId = resolveUserId(headerUserId, authentication);
        List<DiagramShare> shares = shareService.getShares(diagramId, currentUserId);
        List<ShareResponse> response = shares.stream()
                .map(ShareResponse::fromDomain)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @PostMapping
    @Operation(summary = "Tạo liên kết chia sẻ mới", description = "Tạo liên kết chia sẻ với quyền view (chỉ xem) hoặc edit (chỉnh sửa). Chỉ chủ sở hữu mới có quyền.")
    public ResponseEntity<ShareResponse> createShare(
            @PathVariable("id") UUID diagramId,
            @Valid @RequestBody CreateShareRequest request,
            @RequestHeader(value = "X-User-Id", required = false) UUID headerUserId,
            Authentication authentication) {

        UUID currentUserId = resolveUserId(headerUserId, authentication);
        DiagramShare created = shareService.createShare(diagramId, request.role(), currentUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ShareResponse.fromDomain(created));
    }

    @PatchMapping("/{share_id}")
    @Operation(summary = "Thay đổi mức độ quyền chia sẻ", description = "Đổi mức quyền (view <-> edit). Chỉ chủ sở hữu mới có quyền.")
    public ResponseEntity<ShareResponse> updateShare(
            @PathVariable("id") UUID diagramId,
            @PathVariable("share_id") UUID shareId,
            @Valid @RequestBody UpdateShareRequest request,
            @RequestHeader(value = "X-User-Id", required = false) UUID headerUserId,
            Authentication authentication) {

        UUID currentUserId = resolveUserId(headerUserId, authentication);
        DiagramShare updated = shareService.updateShareRole(diagramId, shareId, request.role(), currentUserId);
        return ResponseEntity.ok(ShareResponse.fromDomain(updated));
    }

    @DeleteMapping("/{share_id}")
    @Operation(summary = "Thu hồi liên kết chia sẻ", description = "Vô hiệu hóa liên kết chia sẻ ngay lập tức. Chỉ chủ sở hữu mới có quyền.")
    public ResponseEntity<Void> revokeShare(
            @PathVariable("id") UUID diagramId,
            @PathVariable("share_id") UUID shareId,
            @RequestHeader(value = "X-User-Id", required = false) UUID headerUserId,
            Authentication authentication) {

        UUID currentUserId = resolveUserId(headerUserId, authentication);
        shareService.revokeShare(diagramId, shareId, currentUserId);
        return ResponseEntity.noContent().build();
    }

    private UUID resolveUserId(UUID headerUserId, Authentication authentication) {
        if (headerUserId != null) {
            return headerUserId;
        }
        if (authentication != null && authentication.isAuthenticated()) {
            try {
                return UUID.fromString(authentication.getName());
            } catch (IllegalArgumentException ignored) {
                // Nếu authentication name không phải là UUID (ví dụ username), có thể dùng id mẫu trong lúc dev
            }
        }
        return DEFAULT_SAMPLE_USER_ID;
    }
}
