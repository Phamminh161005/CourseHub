package com.diagramio.domain.model;

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
    private final String diagramType;
    private final String data;
    private final String role; // "view" hoặc "edit"
    private final int version;
    private final boolean isPublic;
    private final Instant createdAt;
    private final Instant updatedAt;

    public SharedDiagramView(UUID diagramId, String title, String diagramType, String data,
                             String role, int version, boolean isPublic,
                             Instant createdAt, Instant updatedAt) {
        this.diagramId = Objects.requireNonNull(diagramId, "diagramId không được để trống");
        this.title = Objects.requireNonNull(title, "title không được để trống");
        this.diagramType = Objects.requireNonNull(diagramType, "diagramType không được để trống");
        this.data = data;
        this.role = Objects.requireNonNull(role, "role không được để trống");
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

    public String getDiagramType() {
        return diagramType;
    }

    public String getData() {
        return data;
    }

    public String getRole() {
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
