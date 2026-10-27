package com.diagramio.domain.access;

import com.diagramio.domain.diagram.Diagram;
import java.util.UUID;

/**
 * Người đang gọi API, do filter xác thực tạo ra: người dùng đăng nhập (JWT) và/hoặc
 * người cầm token chia sẻ (X-Share-Token) của một sơ đồ cụ thể.
 */
public record Caller(UUID userId, UUID sharedDiagramId, ShareRole shareRole) {

    public static Caller user(UUID userId) {
        return new Caller(userId, null, null);
    }

    public static Caller shareToken(UUID diagramId, ShareRole role) {
        return new Caller(null, diagramId, role);
    }

    /** Chưa đăng nhập, không cầm token chia sẻ: chỉ xem được sơ đồ công khai. */
    public static Caller anonymous() {
        return new Caller(null, null, null);
    }

    /** Sơ đồ công khai, hoặc owner, hoặc token chia sẻ (view/edit) của sơ đồ này. */
    public boolean canView(Diagram diagram) {
        return diagram.isPublic() || canViewHistory(diagram);
    }

    /** Chỉ owner: xóa sơ đồ, quản lý liên kết chia sẻ. Token chia sẻ (kể cả edit) không đủ. */
    public boolean canManage(Diagram diagram) {
        return diagram.isOwner(userId);
    }

    /** Owner hoặc token chia sẻ quyền edit của chính sơ đồ này. */
    public boolean canEdit(Diagram diagram) {
        return canManage(diagram) || (sharesDiagram(diagram) && shareRole == ShareRole.EDIT);
    }

    /**
     * Owner hoặc token chia sẻ (view/edit) của sơ đồ này. Cố ý bỏ qua trạng thái công khai:
     * công khai sơ đồ không được làm lộ các snapshot cũ mà owner đã xóa dữ liệu trước khi công khai.
     */
    public boolean canViewHistory(Diagram diagram) {
        return canEdit(diagram) || (sharesDiagram(diagram) && shareRole != null);
    }

    private boolean sharesDiagram(Diagram diagram) {
        return sharedDiagramId != null && sharedDiagramId.equals(diagram.getId());
    }
}
