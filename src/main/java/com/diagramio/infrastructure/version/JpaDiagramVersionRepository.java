package com.diagramio.infrastructure.version;

import com.diagramio.domain.exception.ConflictException;
import com.diagramio.domain.version.DiagramVersion;
import com.diagramio.domain.version.DiagramVersionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.stereotype.Repository;

@Repository
public class JpaDiagramVersionRepository implements DiagramVersionRepository {

    private final SpringDataDiagramVersionRepository repository;
    private final EntityManager entityManager;

    public JpaDiagramVersionRepository(SpringDataDiagramVersionRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Override
    public DiagramVersion save(DiagramVersion version) {
        // persist (không phải merge): snapshot chỉ được thêm mới, không bao giờ ghi đè
        DiagramVersionEntity entity = toEntity(version);
        try {
            entityManager.persist(entity);
            entityManager.flush();
        } catch (PersistenceException ex) {
            // Hai request cùng lấy max + 1 => vi phạm UNIQUE (diagram_id, version_number)
            if (ex instanceof ConstraintViolationException || ex.getCause() instanceof ConstraintViolationException) {
                throw new ConflictException("Another version was saved at the same time, please retry");
            }
            throw ex;
        }
        return toDomain(entity);
    }

    @Override
    public List<DiagramVersion> findByDiagramId(UUID diagramId) {
        return repository.findByDiagramIdOrderByVersionNumberDesc(diagramId).stream()
                .map(JpaDiagramVersionRepository::toDomain)
                .toList();
    }

    @Override
    public Optional<DiagramVersion> findByIdAndDiagramId(UUID id, UUID diagramId) {
        return repository.findByIdAndDiagramId(id, diagramId).map(JpaDiagramVersionRepository::toDomain);
    }

    @Override
    public int maxVersionNumber(UUID diagramId) {
        return repository.maxVersionNumber(diagramId);
    }

    private static DiagramVersionEntity toEntity(DiagramVersion v) {
        return new DiagramVersionEntity(v.id(), v.diagramId(), v.versionNumber(), v.data(), v.note(),
                v.createdBy(), v.createdAt());
    }

    private static DiagramVersion toDomain(DiagramVersionEntity e) {
        return new DiagramVersion(e.getId(), e.getDiagramId(), e.getVersionNumber(), e.getData(), e.getNote(),
                e.getCreatedBy(), e.getCreatedAt());
    }
}
