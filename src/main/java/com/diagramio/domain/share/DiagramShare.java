package com.diagramio.domain.share;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Model nghiệp vụ cho liên kết chia sẻ sơ đồ.
 * Thuần Java (Plain Java), không phụ thuộc bất kỳ framework nào.
 */
public class DiagramShare {

    private final UUID id;
    private final UUID diagramId;
    private final String shareToken;
    private final String role; // "view" hoặc "edit"
    private final Instant createdAt;

    public DiagramShare(UUID id, UUID diagramId, String shareToken, String role, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id không được để trống");
        this.diagramId = Objects.requireNonNull(diagramId, "diagramId không được để trống");
        this.shareToken = Objects.requireNonNull(shareToken, "shareToken không được để trống");
        this.role = validateRole(role);
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public static String validateRole(String role) {
        if (role == null) {
            throw new IllegalArgumentException("Quyền chia sẻ không được để trống");
        }
        String normalized = role.trim().toLowerCase();
        if (!"view".equals(normalized) && !"edit".equals(normalized)) {
            throw new IllegalArgumentException("Quyền chia sẻ chỉ có thể là 'view' hoặc 'edit'");
        }
        return normalized;
    }

    public DiagramShare withRole(String newRole) {
        return new DiagramShare(this.id, this.diagramId, this.shareToken, newRole, this.createdAt);
    }

    public UUID getId() {
        return id;
    }

    public UUID getDiagramId() {
        return diagramId;
    }

    public String getShareToken() {
        return shareToken;
    }

    public String getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
