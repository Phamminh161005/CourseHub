package com.diagramio.domain.diagram;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DiagramRepository {
    Diagram save(Diagram diagram);

    Optional<Diagram> findById(UUID id);

    /** diagramType null = không lọc theo loại; title rỗng = không tìm theo tên. */
    List<Diagram> findByOwnerId(UUID ownerId, DiagramType diagramType, String title, int page, int size);

    long countByOwnerId(UUID ownerId, DiagramType diagramType, String title);

    List<Diagram> findPublicDiagrams(int page, int size);

    long countPublicDiagrams();

    void deleteById(UUID id);
}
