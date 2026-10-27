package com.diagramio.infrastructure.persistence.adapter;

import com.diagramio.domain.model.DiagramShare;
import com.diagramio.domain.repository.DiagramShareRepository;
import com.diagramio.infrastructure.mapper.DiagramShareMapper;
import com.diagramio.infrastructure.persistence.entity.DiagramShareEntity;
import com.diagramio.infrastructure.persistence.repository.JpaDiagramShareRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class DiagramShareRepositoryImpl implements DiagramShareRepository {

    private final JpaDiagramShareRepository jpaRepository;
    private final DiagramShareMapper mapper;

    public DiagramShareRepositoryImpl(JpaDiagramShareRepository jpaRepository, DiagramShareMapper mapper) {
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
