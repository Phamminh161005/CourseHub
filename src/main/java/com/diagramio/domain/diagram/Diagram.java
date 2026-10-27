package com.diagramio.domain.diagram;

import java.time.Instant;
import java.util.UUID;

/** Pure domain model for a diagram. */
public class Diagram {

    private UUID id;
    private String title;
    private String diagramType;
    private String data;
    private Integer version;
    private boolean isPublic;
    private UUID ownerId;
    private UUID updatedBy;
    private Instant createdAt;
    private Instant updatedAt;

    public Diagram() {
    }

    public Diagram(UUID id, String title, String diagramType, String data, Integer version,
                   boolean isPublic, UUID ownerId, UUID updatedBy, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.title = title;
        this.diagramType = diagramType;
        this.data = data;
        this.version = version;
        this.isPublic = isPublic;
        this.ownerId = ownerId;
        this.updatedBy = updatedBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public boolean isOwner(UUID userId) {
        return ownerId != null && ownerId.equals(userId);
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDiagramType() { return diagramType; }
    public void setDiagramType(String diagramType) { this.diagramType = diagramType; }
    public String getData() { return data; }
    public void setData(String data) { this.data = data; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public boolean isPublic() { return isPublic; }
    public void setPublic(boolean isPublic) { this.isPublic = isPublic; }
    public UUID getOwnerId() { return ownerId; }
    public void setOwnerId(UUID ownerId) { this.ownerId = ownerId; }
    public UUID getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(UUID updatedBy) { this.updatedBy = updatedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
