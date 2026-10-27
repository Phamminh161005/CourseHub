package com.diagramio.domain.share;

import com.diagramio.domain.access.ShareRole;
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
    private final ShareRole role;
    private final Instant createdAt;

    public DiagramShare(UUID id, UUID diagramId, String shareToken, ShareRole role, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id không được để trống");
        this.diagramId = Objects.requireNonNull(diagramId, "diagramId không được để trống");
        this.shareToken = Objects.requireNonNull(shareToken, "shareToken không được để trống");
        this.role = Objects.requireNonNull(role, "role không được để trống");
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public DiagramShare withRole(ShareRole newRole) {
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

    public ShareRole getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
