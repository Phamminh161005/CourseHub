package com.diagramio.config;

import com.diagramio.domain.TransactionRunner;
import com.diagramio.domain.diagram.DiagramRepository;
import com.diagramio.domain.version.DiagramVersionRepository;
import com.diagramio.domain.version.DiagramVersionService;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DiagramVersionConfig {

    @Bean
    public DiagramVersionService diagramVersionService(DiagramRepository diagramRepository,
                                                       DiagramVersionRepository versionRepository,
                                                       TransactionRunner transactionRunner) {
        return new DiagramVersionService(diagramRepository, versionRepository, transactionRunner, Clock.systemUTC());
    }
}
