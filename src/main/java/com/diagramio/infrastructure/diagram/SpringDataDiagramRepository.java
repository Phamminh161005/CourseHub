package com.diagramio.infrastructure.diagram;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataDiagramRepository
        extends JpaRepository<DiagramEntity, UUID>, JpaSpecificationExecutor<DiagramEntity> {

    Page<DiagramEntity> findByIsPublicTrue(Pageable pageable);

    long countByIsPublicTrue();

    @Query("select d.version from DiagramEntity d where d.id = :id")
    Optional<Integer> findVersionById(@Param("id") UUID id);

    /**
     * Chỉ ghi khi version trong CSDL vẫn bằng expectedVersion (optimistic locking ở mức câu lệnh SQL).
     * Trả về số dòng được cập nhật: 0 nghĩa là đã có người khác ghi trước.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update DiagramEntity d
               set d.title = :title, d.diagramType = :diagramType, d.data = :data, d.version = :newVersion,
                   d.isPublic = :isPublic, d.updatedBy = :updatedBy, d.updatedAt = :updatedAt
             where d.id = :id and d.version = :expectedVersion
            """)
    int updateIfVersionMatches(@Param("id") UUID id,
                               @Param("expectedVersion") Integer expectedVersion,
                               @Param("newVersion") Integer newVersion,
                               @Param("title") String title,
                               @Param("diagramType") String diagramType,
                               @Param("data") String data,
                               @Param("isPublic") boolean isPublic,
                               @Param("updatedBy") UUID updatedBy,
                               @Param("updatedAt") Instant updatedAt);
}
