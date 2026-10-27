package com.diagramio.infrastructure.version;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Bảng diagram_versions. Mọi cột đều updatable = false vì snapshot là bất biến. */
@Entity
@Table(name = "diagram_versions")
public class DiagramVersionEntity {

    @Id
    @Column(name = "id", updatable = false)
    private UUID id;

    @Column(name = "diagram_id", nullable = false, updatable = false)
    private UUID diagramId;

    @Column(name = "version_number", nullable = false, updatable = false)
    private int versionNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data", nullable = false, updatable = false, columnDefinition = "jsonb")
    private String data;

    @Column(name = "note", updatable = false)
    private String note;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    protected DiagramVersionEntity() {
    }

    public DiagramVersionEntity(UUID id, UUID diagramId, int versionNumber, String data, String note,
                                UUID createdBy, Instant createdAt) {
        this.id = id;
        this.diagramId = diagramId;
        this.versionNumber = versionNumber;
        this.data = data;
        this.note = note;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getDiagramId() { return diagramId; }
    public int getVersionNumber() { return versionNumber; }
    public String getData() { return data; }
    public String getNote() { return note; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
