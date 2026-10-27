package com.diagramio.infrastructure.persistence.adapter;

import com.diagramio.domain.model.Diagram;
import com.diagramio.domain.repository.DiagramRepository;
import com.diagramio.infrastructure.mapper.DiagramMapper;
import com.diagramio.infrastructure.persistence.entity.DiagramEntity;
import com.diagramio.infrastructure.persistence.repository.JpaDiagramRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class DiagramRepositoryImpl implements DiagramRepository {

    private final JpaDiagramRepository jpaRepository;
    private final DiagramMapper mapper;

    public DiagramRepositoryImpl(JpaDiagramRepository jpaRepository, DiagramMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<Diagram> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Diagram save(Diagram diagram) {
        DiagramEntity entity = mapper.toEntity(diagram);
        DiagramEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }
}
