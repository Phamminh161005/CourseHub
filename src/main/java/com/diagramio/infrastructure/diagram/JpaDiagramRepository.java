package com.diagramio.infrastructure.diagram;

import com.diagramio.domain.diagram.Diagram;
import com.diagramio.domain.diagram.DiagramRepository;
import com.diagramio.domain.exception.OptimisticLockConflictException;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

/**
 * Adapter JPA cho DiagramRepository. Khi cập nhật, domain đã tăng version lên 1 nên ở đây chỉ ghi
 * nếu CSDL còn đúng version - 1; nếu không thì có người khác vừa ghi và trả về 409.
 */
@Repository
public class JpaDiagramRepository implements DiagramRepository {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "updatedAt");

    private final SpringDataDiagramRepository repository;

    public JpaDiagramRepository(SpringDataDiagramRepository repository) {
        this.repository = repository;
    }

    @Override
    public Diagram save(Diagram diagram) {
        if (diagram.getId() == null) {
            diagram.setId(UUID.randomUUID());
            return toDomain(repository.save(toEntity(diagram)));
        }
        int expectedVersion = diagram.getVersion() - 1;
        int updated = repository.updateIfVersionMatches(diagram.getId(), expectedVersion, diagram.getVersion(),
                diagram.getTitle(), diagram.getDiagramType(), diagram.getData(), diagram.isPublic(),
                diagram.getUpdatedBy(), diagram.getUpdatedAt());
        if (updated == 1) {
            return diagram;
        }
        Optional<Integer> currentVersion = repository.findVersionById(diagram.getId());
        if (currentVersion.isEmpty()) {
            // Sơ đồ mới nhưng domain đã gán sẵn id
            return toDomain(repository.save(toEntity(diagram)));
        }
        throw new OptimisticLockConflictException(diagram.getId(), expectedVersion, currentVersion.get());
    }

    @Override
    public Optional<Diagram> findById(UUID id) {
        return repository.findById(id).map(JpaDiagramRepository::toDomain);
    }

    @Override
    public List<Diagram> findByOwnerId(UUID ownerId, String diagramType, String title, int page, int size) {
        return repository.findAll(ownedBy(ownerId, diagramType, title), PageRequest.of(page, size, NEWEST_FIRST))
                .map(JpaDiagramRepository::toDomain)
                .getContent();
    }

    @Override
    public long countByOwnerId(UUID ownerId, String diagramType, String title) {
        return repository.count(ownedBy(ownerId, diagramType, title));
    }

    @Override
    public List<Diagram> findPublicDiagrams(int page, int size) {
        return repository.findByIsPublicTrue(PageRequest.of(page, size, NEWEST_FIRST))
                .map(JpaDiagramRepository::toDomain)
                .getContent();
    }

    @Override
    public long countPublicDiagrams() {
        return repository.countByIsPublicTrue();
    }

    @Override
    public void deleteById(UUID id) {
        repository.deleteById(id);
    }

    private static Specification<DiagramEntity> ownedBy(UUID ownerId, String diagramType, String title) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("ownerId"), ownerId));
            if (diagramType != null && !diagramType.isBlank()) {
                predicates.add(cb.equal(root.get("diagramType"), diagramType));
            }
            if (title != null && !title.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("title")), "%" + title.toLowerCase(Locale.ROOT) + "%"));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static DiagramEntity toEntity(Diagram d) {
        return new DiagramEntity(d.getId(), d.getTitle(), d.getDiagramType(), d.getData(), d.getVersion(),
                d.isPublic(), d.getOwnerId(), d.getUpdatedBy(), d.getCreatedAt(), d.getUpdatedAt());
    }

    private static Diagram toDomain(DiagramEntity e) {
        return new Diagram(e.getId(), e.getTitle(), e.getDiagramType(), e.getData(), e.getVersion(), e.isPublic(),
                e.getOwnerId(), e.getUpdatedBy(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
