package com.diagramio.infrastructure.share;

import com.diagramio.domain.share.DiagramShare;
import com.diagramio.domain.share.DiagramShareRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class DiagramShareRepositoryAdapter implements DiagramShareRepository {

    private final DiagramShareJpaRepository jpaRepository;
    private final DiagramShareMapper mapper;

    public DiagramShareRepositoryAdapter(DiagramShareJpaRepository jpaRepository, DiagramShareMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public List<DiagramShare> findByDiagramId(UUID diagramId) {
        return jpaRepository.findByDiagramId(diagramId)
                .stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<DiagramShare> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<DiagramShare> findByShareToken(String shareToken) {
        return jpaRepository.findByShareToken(shareToken).map(mapper::toDomain);
    }

    @Override
    public DiagramShare save(DiagramShare share) {
        DiagramShareEntity entity = mapper.toEntity(share);
        DiagramShareEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public boolean existsByShareToken(String shareToken) {
        return jpaRepository.existsByShareToken(shareToken);
    }
}
