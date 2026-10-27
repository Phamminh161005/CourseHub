package com.diagramio.domain.diagram;

import com.diagramio.domain.access.Caller;
import com.diagramio.domain.exception.DiagramNotFoundException;
import com.diagramio.domain.exception.InvalidInputException;
import com.diagramio.domain.exception.OptimisticLockConflictException;
import com.diagramio.domain.exception.UnauthorizedDiagramAccessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Domain rules for diagram management. This class intentionally has no framework dependency. */
public class DiagramService {

    public static final String DEFAULT_DATA = "{\"nodes\": [], \"edges\": []}";
    public static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_DATA_BYTES = 2 * 1024 * 1024;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String UML = "UML";
    private static final String ERD = "ERD";

    private final DiagramRepository repository;

    public DiagramService(DiagramRepository repository) {
        this.repository = repository;
    }

    public Diagram createDiagram(Diagram diagram) {
        if (diagram == null) {
            throw new InvalidInputException("Diagram is required");
        }
        validateDiagramType(diagram.getDiagramType());
        String data = diagram.getData();
        if (data == null || data.isBlank()) {
            data = DEFAULT_DATA;
        }
        validateJson(data);

        Instant now = Instant.now();
        diagram.setData(data);
        diagram.setVersion(1);
        diagram.setPublic(false);
        diagram.setCreatedAt(now);
        diagram.setUpdatedAt(now);
        return repository.save(diagram);
    }

    /**
     * Owner hoặc người cầm link edit được sửa. Chỉ owner được đổi trạng thái công khai;
     * người sửa qua link gửi isPublic khác thì giữ nguyên giá trị cũ.
     * updatedBy là người đăng nhập, NULL nếu sửa qua link mà không đăng nhập.
     */
    public Diagram updateDiagram(UUID id, String title, String diagramType, String data,
                                 boolean isPublic, Caller caller, Integer requestVersion) {
        if (id == null) {
            throw new InvalidInputException("Diagram id is required");
        }
        Diagram current = findRequired(id);
        if (!caller.canEdit(current)) {
            throw new UnauthorizedDiagramAccessException("You do not have permission to edit this diagram");
        }
        checkVersion(id, requestVersion, current.getVersion());
        validateDiagramType(diagramType);
        validateJson(data);

        current.setTitle(title);
        current.setDiagramType(diagramType);
        current.setData(data);
        if (caller.canManage(current)) {
            current.setPublic(isPublic);
        }
        current.setUpdatedBy(caller.userId());
        current.setUpdatedAt(Instant.now());
        return repository.save(current);
    }

    public void deleteDiagram(UUID id, Caller caller) {
        Diagram diagram = findRequired(id);
        if (!caller.canManage(diagram)) {
            throw new UnauthorizedDiagramAccessException("Only the diagram owner can delete it");
        }
        repository.deleteById(id);
    }

    public Diagram getDiagramById(UUID id, Caller caller) {
        Diagram diagram = findRequired(id);
        if (!caller.canView(diagram)) {
            throw new UnauthorizedDiagramAccessException("You do not have access to this diagram");
        }
        return diagram;
    }

    public List<Diagram> getDiagramsByOwner(UUID ownerId, String diagramType, String title,
                                            int page, int size) {
        validatePage(page, size);
        if (diagramType != null && !diagramType.isBlank()) {
            validateDiagramType(diagramType);
        }
        return repository.findByOwnerId(ownerId, normalizeFilter(diagramType), normalizeFilter(title), page, size);
    }

    public long countDiagramsByOwner(UUID ownerId, String diagramType, String title) {
        if (diagramType != null && !diagramType.isBlank()) {
            validateDiagramType(diagramType);
        }
        return repository.countByOwnerId(ownerId, normalizeFilter(diagramType), normalizeFilter(title));
    }

    public List<Diagram> getPublicDiagrams(int page, int size) {
        validatePage(page, size);
        return repository.findPublicDiagrams(page, size);
    }

    public long countPublicDiagrams() {
        return repository.countPublicDiagrams();
    }

    private Diagram findRequired(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new DiagramNotFoundException("Diagram not found: " + id));
    }

    private void checkVersion(UUID id, Integer requestedVersion, Integer currentVersion) {
        if (requestedVersion == null || currentVersion == null || !requestedVersion.equals(currentVersion)) {
            throw new OptimisticLockConflictException(id, requestedVersion, currentVersion);
        }
    }

    private void validateDiagramType(String diagramType) {
        if (!UML.equals(diagramType) && !ERD.equals(diagramType)) {
            throw new InvalidInputException("diagramType must be UML or ERD");
        }
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidInputException("page must be non-negative and size must be between 1 and " + MAX_PAGE_SIZE);
        }
    }

    private String normalizeFilter(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }

    /** data phải là object JSON có mảng "nodes" và "edges", tối đa 2 MB. */
    private void validateJson(String data) {
        if (data == null || data.isBlank()) {
            throw new InvalidInputException("data must be valid JSON");
        }
        if (data.getBytes(StandardCharsets.UTF_8).length > MAX_DATA_BYTES) {
            throw new InvalidInputException("data must not exceed 2 MB");
        }
        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(data);
        } catch (JsonProcessingException e) {
            throw new InvalidInputException("data must be valid JSON");
        }
        if (root == null || !root.isObject() || !root.path("nodes").isArray() || !root.path("edges").isArray()) {
            throw new InvalidInputException("data must be a JSON object with \"nodes\" and \"edges\" arrays");
        }
    }
}
