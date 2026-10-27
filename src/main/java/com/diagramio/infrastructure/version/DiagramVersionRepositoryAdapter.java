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
public class DiagramVersionRepositoryAdapter implements DiagramVersionRepository {

    private final DiagramVersionJpaRepository jpaRepository;
    private final EntityManager entityManager;

    public DiagramVersionRepositoryAdapter(DiagramVersionJpaRepository jpaRepository, EntityManager entityManager) {
        this.jpaRepository = jpaRepository;
        this.entityManager = entityManager;
    }

    @Override
    public void lockDiagram(UUID diagramId) {
        jpaRepository.lockDiagramRow(diagramId);
    }

    @Override
    public DiagramVersion save(DiagramVersion version) {
        // persist (không phải merge): snapshot chỉ được thêm mới, không bao giờ ghi đè
        DiagramVersionEntity entity = DiagramVersionEntity.fromDomain(version);
        try {
            entityManager.persist(entity);
            entityManager.flush();
        } catch (PersistenceException ex) {
            // Lưới an toàn: bình thường lockDiagram đã xếp hàng các yêu cầu nên không trùng số thứ tự
            if (ex instanceof ConstraintViolationException || ex.getCause() instanceof ConstraintViolationException) {
                throw new ConflictException("Another version was saved at the same time, please retry");
            }
            throw ex;
        }
        return entity.toDomain();
    }

    @Override
    public List<DiagramVersion> findByDiagramId(UUID diagramId) {
        return jpaRepository.findByDiagramIdOrderByVersionNumberDesc(diagramId).stream()
                .map(DiagramVersionEntity::toDomain)
                .toList();
    }

    @Override
    public Optional<DiagramVersion> findByIdAndDiagramId(UUID id, UUID diagramId) {
        return jpaRepository.findByIdAndDiagramId(id, diagramId).map(DiagramVersionEntity::toDomain);
    }

    @Override
    public int maxVersionNumber(UUID diagramId) {
        return jpaRepository.maxVersionNumber(diagramId);
    }
}
