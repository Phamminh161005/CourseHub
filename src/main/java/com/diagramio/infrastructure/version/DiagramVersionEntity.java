package com.diagramio.infrastructure.version;

import com.diagramio.domain.version.DiagramVersion;
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
        // JPA cần constructor không tham số
    }

    static DiagramVersionEntity fromDomain(DiagramVersion version) {
        DiagramVersionEntity entity = new DiagramVersionEntity();
        entity.id = version.id();
        entity.diagramId = version.diagramId();
        entity.versionNumber = version.versionNumber();
        entity.data = version.data();
        entity.note = version.note();
        entity.createdBy = version.createdBy();
        entity.createdAt = version.createdAt();
        return entity;
    }

    DiagramVersion toDomain() {
        return new DiagramVersion(id, diagramId, versionNumber, data, note, createdBy, createdAt);
    }
}
