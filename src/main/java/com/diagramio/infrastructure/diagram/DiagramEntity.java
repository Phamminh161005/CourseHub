package com.diagramio.infrastructure.diagram;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Bảng diagrams. Cột version do domain tăng thủ công và được kiểm tra khi UPDATE. */
@Entity
@Table(name = "diagrams")
public class DiagramEntity {

    @Id
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "diagram_type", nullable = false)
    private String diagramType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data", nullable = false, columnDefinition = "jsonb")
    private String data;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "is_public", nullable = false)
    private boolean isPublic;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected DiagramEntity() {
    }

    public DiagramEntity(UUID id, String title, String diagramType, String data, Integer version, boolean isPublic,
                         UUID ownerId, UUID updatedBy, Instant createdAt, Instant updatedAt) {
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

    public UUID getId() { return id; }
    public String getTitle() { return title; }
    public String getDiagramType() { return diagramType; }
    public String getData() { return data; }
    public Integer getVersion() { return version; }
    public boolean isPublic() { return isPublic; }
    public UUID getOwnerId() { return ownerId; }
    public UUID getUpdatedBy() { return updatedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
