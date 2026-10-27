package com.diagramio.domain.diagram;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DiagramRepository {
    Diagram save(Diagram diagram);

    Optional<Diagram> findById(UUID id);

    List<Diagram> findByOwnerId(UUID ownerId, String diagramType, String title, int page, int size);

    long countByOwnerId(UUID ownerId, String diagramType, String title);

    List<Diagram> findPublicDiagrams(int page, int size);

    long countPublicDiagrams();

    void deleteById(UUID id);
}
