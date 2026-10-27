package com.diagramio.infrastructure.share;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "diagram_shares")
public class DiagramShareEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "diagram_id", nullable = false)
    private UUID diagramId;

    @Column(name = "share_token", nullable = false, unique = true, length = 64)
    private String shareToken;

    @Column(name = "role", nullable = false, length = 10)
    private String role;

    @Column(name = "created_at")
    private Instant createdAt;

    public DiagramShareEntity() {
    }

    public DiagramShareEntity(UUID id, UUID diagramId, String shareToken, String role, Instant createdAt) {
        this.id = id;
        this.diagramId = diagramId;
        this.shareToken = shareToken;
        this.role = role;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getDiagramId() {
        return diagramId;
    }

    public void setDiagramId(UUID diagramId) {
        this.diagramId = diagramId;
    }

    public String getShareToken() {
        return shareToken;
    }

    public void setShareToken(String shareToken) {
        this.shareToken = shareToken;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
