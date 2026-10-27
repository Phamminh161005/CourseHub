package com.diagramio.config;

import com.diagramio.domain.diagram.DiagramRepository;
import com.diagramio.domain.diagram.DiagramService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DiagramConfig {

    @Bean
    public DiagramService diagramService(DiagramRepository diagramRepository) {
        return new DiagramService(diagramRepository);
    }
}
