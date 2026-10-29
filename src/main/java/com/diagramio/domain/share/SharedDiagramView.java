package com.diagramio.domain.share;

import com.diagramio.domain.access.ShareRole;
import com.diagramio.domain.diagram.DiagramType;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Model biểu diễn thông tin và cấu trúc sơ đồ trả về khi truy xuất qua mã chia sẻ (public share token).
 * Thuần Java.
 */
public class SharedDiagramView {

    private final UUID diagramId;
    private final String title;
    private final DiagramType diagramType;
    private final String data;
    private final ShareRole role;
    private final int version;
    private final boolean isPublic;
    private final Instant createdAt;
    private final Instant updatedAt;

    public SharedDiagramView(UUID diagramId, String title, DiagramType diagramType, String data,
                             ShareRole role, int version, boolean isPublic,
                             Instant createdAt, Instant updatedAt) {
        this.diagramId = Objects.requireNonNull(diagramId, "diagramId must not be null");
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.diagramType = Objects.requireNonNull(diagramType, "diagramType must not be null");
        this.data = data;
        this.role = Objects.requireNonNull(role, "role must not be null");
        this.version = version;
        this.isPublic = isPublic;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getDiagramId() {
        return diagramId;
    }

    public String getTitle() {
        return title;
    }

    public DiagramType getDiagramType() {
        return diagramType;
    }

    public String getData() {
        return data;
    }

    public ShareRole getRole() {
        return role;
    }

    public int getVersion() {
        return version;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
