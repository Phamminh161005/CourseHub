package com.diagramio.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Model nghiệp vụ cho sơ đồ (Diagram).
 * Thuần Java (Plain Java).
 */
public class Diagram {

    private final UUID id;
    private final String title;
    private final String diagramType; // UML hoặc ERD
    private final String data;
    private final int version;
    private final boolean isPublic;
    private final UUID ownerId;
    private final UUID updatedBy;
    private final Instant createdAt;
    private final Instant updatedAt;

    public Diagram(UUID id, String title, String diagramType, String data, int version,
                   boolean isPublic, UUID ownerId, UUID updatedBy,
                   Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id không được để trống");
        this.title = Objects.requireNonNull(title, "title không được để trống");
        this.diagramType = Objects.requireNonNull(diagramType, "diagramType không được để trống");
        this.data = data != null ? data : "{\"nodes\": [], \"edges\": []}";
        this.version = version;
        this.isPublic = isPublic;
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId không được để trống");
        this.updatedBy = updatedBy;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }

    public UUID getId() {
        return id;
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

    public int getVersion() {
        return version;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
