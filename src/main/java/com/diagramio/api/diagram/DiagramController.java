package com.diagramio.api.diagram;

import com.diagramio.api.CallerResolver;
import com.diagramio.api.diagram.dto.CreateDiagramRequest;
import com.diagramio.api.diagram.dto.DiagramResponse;
import com.diagramio.api.diagram.dto.DiagramSummaryResponse;
import com.diagramio.api.diagram.dto.PageResponse;
import com.diagramio.api.diagram.dto.UpdateDiagramRequest;
import com.diagramio.domain.diagram.Diagram;
import com.diagramio.domain.diagram.DiagramService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/diagrams")
public class DiagramController {

    private final DiagramService diagramService;

    public DiagramController(DiagramService diagramService) {
        this.diagramService = diagramService;
    }

    @GetMapping
    public PageResponse<DiagramSummaryResponse> getMyDiagrams(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String diagramType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        UUID ownerId = currentUserId(authentication);
        List<Diagram> diagrams = diagramService.getDiagramsByOwner(ownerId, diagramType, title, page, size);
        long total = diagramService.countDiagramsByOwner(ownerId, diagramType, title);
        return pageResponse(diagrams, page, size, total);
    }

    @PostMapping
    public ResponseEntity<DiagramResponse> create(
            @Valid @RequestBody CreateDiagramRequest request,
            Authentication authentication) {
        Diagram diagram = new Diagram();
        diagram.setTitle(request.title());
        diagram.setDiagramType(request.diagramType());
        diagram.setPublic(request.isPublic());
        diagram.setOwnerId(currentUserId(authentication));
        Diagram created = diagramService.createDiagram(diagram);
        return ResponseEntity.created(URI.create("/api/v1/diagrams/" + created.getId()))
                .body(DiagramResponse.from(created));
    }

    @GetMapping("/public")
    public PageResponse<DiagramSummaryResponse> getPublicDiagrams(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<Diagram> diagrams = diagramService.getPublicDiagrams(page, size);
        return pageResponse(diagrams, page, size, diagramService.countPublicDiagrams());
    }

    @GetMapping("/{id}")
    public DiagramResponse getById(@PathVariable UUID id, Authentication authentication) {
        return DiagramResponse.from(diagramService.getDiagramById(id, CallerResolver.resolveOrAnonymous(authentication)));
    }

    @PutMapping("/{id}")
    public DiagramResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDiagramRequest request,
            Authentication authentication) {
        return DiagramResponse.from(diagramService.updateDiagram(
                id, request.title(), request.diagramType(), request.data(), request.isPublic(),
                CallerResolver.resolve(authentication), request.version()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, Authentication authentication) {
        diagramService.deleteDiagram(id, CallerResolver.resolve(authentication));
        return ResponseEntity.noContent().build();
    }

    private UUID currentUserId(Authentication authentication) {
        return CallerResolver.requireUserId(authentication);
    }

    private <T> PageResponse<DiagramSummaryResponse> pageResponse(
            List<Diagram> diagrams, int page, int size, long total) {
        List<DiagramSummaryResponse> content = diagrams.stream()
                .map(DiagramSummaryResponse::from)
                .toList();
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / size);
        return new PageResponse<>(content, page, size, total, totalPages);
    }
}
