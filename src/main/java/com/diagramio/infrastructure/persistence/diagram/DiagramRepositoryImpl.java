package com.diagramio.infrastructure.persistence.diagram;

import com.diagramio.domain.diagram.Diagram;
import com.diagramio.domain.diagram.DiagramRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository("legacyDiagramRepository")
public class DiagramRepositoryImpl implements DiagramRepository {

    private final SpringDataDiagramJpaRepository repository;
    private final DiagramMapper mapper;

    public DiagramRepositoryImpl(SpringDataDiagramJpaRepository repository, DiagramMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Diagram save(Diagram diagram) {
        return mapper.toDomain(repository.save(mapper.toEntity(diagram)));
    }

    @Override
    public Optional<Diagram> findById(UUID id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<Diagram> findByOwnerId(UUID ownerId, String diagramType, String title, int page, int size) {
        return findPage(ownerId, diagramType, title, PageRequest.of(page, size))
                .map(mapper::toDomain)
                .getContent();
    }

    @Override
    public long countByOwnerId(UUID ownerId, String diagramType, String title) {
        return findPage(ownerId, diagramType, title, PageRequest.of(0, 1)).getTotalElements();
    }

    @Override
    public List<Diagram> findPublicDiagrams(int page, int size) {
        return repository.findByIsPublicTrue(PageRequest.of(page, size))
                .map(mapper::toDomain)
                .getContent();
    }

    @Override
    public long countPublicDiagrams() {
        return repository.findByIsPublicTrue(PageRequest.of(0, 1)).getTotalElements();
    }

    @Override
    public void deleteById(UUID id) {
        repository.deleteById(id);
    }

    private org.springframework.data.domain.Page<DiagramEntity> findPage(
            UUID ownerId, String diagramType, String title, PageRequest pageRequest) {
        boolean hasDiagramType = diagramType != null && !diagramType.isBlank();
        boolean hasTitle = title != null && !title.isBlank();

        if (hasDiagramType && hasTitle) {
            return repository.findByOwnerIdAndDiagramTypeContainingAndTitleContainingIgnoreCase(
                    ownerId, diagramType, title, pageRequest);
        }
        if (hasDiagramType) {
            return repository.findByOwnerIdAndDiagramTypeContaining(ownerId, diagramType, pageRequest);
        }
        if (hasTitle) {
            return repository.findByOwnerIdAndTitleContainingIgnoreCase(ownerId, title, pageRequest);
        }
        return repository.findByOwnerId(ownerId, pageRequest);
    }
}
