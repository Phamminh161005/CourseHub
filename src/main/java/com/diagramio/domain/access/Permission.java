package com.diagramio.domain.access;

import com.diagramio.domain.diagram.Diagram;

/**
 * Các mức quyền trên một sơ đồ, mỗi mức ứng với một luật trong {@link Caller}.
 * Service gọi {@link DiagramAccess#require} với mức cần thiết thay vì tự kiểm tra.
 */
public enum Permission {
    /** Xem nội dung: sơ đồ công khai, owner, hoặc link view/edit. */
    VIEW("You do not have access to this diagram"),
    /** Xem lịch sử phiên bản: owner hoặc link view/edit (công khai không mở lịch sử). */
    VIEW_HISTORY("You do not have access to this diagram's version history"),
    /** Sửa sơ đồ, lưu và khôi phục phiên bản: owner hoặc link edit. */
    EDIT("You do not have permission to edit this diagram"),
    /** Xóa sơ đồ, quản lý link chia sẻ: chỉ owner. */
    MANAGE("Only the diagram owner can do this");

    private final String deniedMessage;

    Permission(String deniedMessage) {
        this.deniedMessage = deniedMessage;
    }

    public boolean isGrantedTo(Caller caller, Diagram diagram) {
        return switch (this) {
            case VIEW -> caller.canView(diagram);
            case VIEW_HISTORY -> caller.canViewHistory(diagram);
            case EDIT -> caller.canEdit(diagram);
            case MANAGE -> caller.canManage(diagram);
        };
    }

    public String deniedMessage() {
        return deniedMessage;
    }
}
