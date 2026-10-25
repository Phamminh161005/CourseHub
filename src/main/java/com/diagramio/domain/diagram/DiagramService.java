package com.diagramio.domain.diagram;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.diagramio.domain.exception.DiagramNotFoundException;
import com.diagramio.domain.exception.InvalidInputException;
import com.diagramio.domain.exception.OptimisticLockConflictException;
import com.diagramio.domain.exception.UnauthorizedDiagramAccessException;

/** Domain rules for diagram management. This class intentionally has no framework dependency. */
public class DiagramService {

    public static final String DEFAULT_DATA = "{\"nodes\":[], \"edges\":[]}";
    private static final int MAX_DATA_BYTES = 2 * 1024 * 1024;
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

    public Diagram updateDiagram(Diagram requested, Integer requestVersion) {
        if (requested == null || requested.getId() == null) {
            throw new InvalidInputException("Diagram id is required");
        }
        Diagram current = findRequired(requested.getId());
        checkVersion(requested.getId(), requestVersion, current.getVersion());
        validateDiagramType(requested.getDiagramType());
        validateJson(requested.getData());

        current.setTitle(requested.getTitle());
        current.setDiagramType(requested.getDiagramType());
        current.setData(requested.getData());
        current.setPublic(requested.isPublic());
        current.setUpdatedBy(requested.getUpdatedBy());
        current.setVersion(current.getVersion() + 1);
        current.setUpdatedAt(Instant.now());
        return repository.save(current);
    }

    public Diagram updateDiagram(UUID id, String title, String diagramType, String data,
                                 boolean isPublic, UUID updatedBy, Integer requestVersion) {
        Diagram requested = new Diagram();
        requested.setId(id);
        requested.setTitle(title);
        requested.setDiagramType(diagramType);
        requested.setData(data);
        requested.setPublic(isPublic);
        requested.setUpdatedBy(updatedBy);
        return updateDiagram(requested, requestVersion);
    }

    public void deleteDiagram(UUID id, UUID currentUserId) {
        Diagram diagram = findRequired(id);
        if (!diagram.isOwner(currentUserId)) {
            throw new UnauthorizedDiagramAccessException("Only the diagram owner can delete it");
        }
        repository.deleteById(id);
    }

    public Diagram getDiagramById(UUID id, UUID currentUserId, boolean hasPermission) {
        Diagram diagram = findRequired(id);
        if (!diagram.canAccess(currentUserId, hasPermission)) {
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
        return repository.findByOwnerId(ownerId, diagramType, normalizeTitle(title), page, size);
    }

    public long countDiagramsByOwner(UUID ownerId, String diagramType, String title) {
        if (diagramType != null && !diagramType.isBlank()) {
            validateDiagramType(diagramType);
        }
        return repository.countByOwnerId(ownerId, diagramType, normalizeTitle(title));
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
        if (page < 0 || size < 1) {
            throw new InvalidInputException("page must be non-negative and size must be positive");
        }
    }

    private String normalizeTitle(String title) {
        return title == null || title.isBlank() ? null : title.trim();
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper OBJECT_MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();

    private void validateJson(String data) {
        if (data == null || data.isBlank()) {
            throw new InvalidInputException("data must be valid JSON");
        }
        if (data.getBytes(StandardCharsets.UTF_8).length > MAX_DATA_BYTES) {
            throw new InvalidInputException("data must not exceed 2 MB");
        }
        try {
             OBJECT_MAPPER.readTree(data);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
        throw new InvalidInputException("data must be valid JSON");
        }
    }

    private static final class JsonValidator {
        private final String input;
        private int position;

        private JsonValidator(String input) {
            this.input = input;
        }

        private void validate() {
            skipWhitespace();
            parseValue();
            skipWhitespace();
            if (position != input.length()) {
                throw new IllegalArgumentException();
            }
        }

        private void parseValue() {
            skipWhitespace();
            if (position >= input.length()) {
                throw new IllegalArgumentException();
            }
            char value = input.charAt(position);
            if (value == '{') parseObject();
            else if (value == '[') parseArray();
            else if (value == '"') parseString();
            else if (value == 't') parseLiteral("true");
            else if (value == 'f') parseLiteral("false");
            else if (value == 'n') parseLiteral("null");
            else parseNumber();
        }

        private void parseObject() {
            position++;
            skipWhitespace();
            if (consume('}')) return;
            while (true) {
                parseString();
                skipWhitespace();
                require(':');
                parseValue();
                skipWhitespace();
                if (consume('}')) return;
                require(',');
            }
        }

        private void parseArray() {
            position++;
            skipWhitespace();
            if (consume(']')) return;
            while (true) {
                parseValue();
                skipWhitespace();
                if (consume(']')) return;
                require(',');
            }
        }

        private void parseString() {
            require('"');
            while (position < input.length()) {
                char value = input.charAt(position++);
                if (value == '"') return;
                if (value == '\\') {
                    if (position >= input.length()) throw new IllegalArgumentException();
                    char escaped = input.charAt(position++);
                    if (escaped == 'u') {
                        for (int i = 0; i < 4; i++) {
                            if (position >= input.length() || Character.digit(input.charAt(position++), 16) < 0) {
                                throw new IllegalArgumentException();
                            }
                        }
                    } else if ("\"\\/bfnrt".indexOf(escaped) < 0) {
                        throw new IllegalArgumentException();
                    }
                } else if (value < 0x20) {
                    throw new IllegalArgumentException();
                }
            }
            throw new IllegalArgumentException();
        }

        private void parseNumber() {
            int start = position;
            consume('-');
            if (consume('0')) {
                // A leading zero may not be followed by another digit.
                if (position < input.length() && Character.isDigit(input.charAt(position))) {
                    throw new IllegalArgumentException();
                }
            } else {
                requireDigits();
            }
            if (consume('.')) requireDigits();
            if (position < input.length() && (input.charAt(position) == 'e' || input.charAt(position) == 'E')) {
                position++;
                if (position < input.length() && (input.charAt(position) == '+' || input.charAt(position) == '-')) {
                    position++;
                }
                requireDigits();
            }
            if (start == position) throw new IllegalArgumentException();
        }

        private void parseLiteral(String literal) {
            if (!input.startsWith(literal, position)) throw new IllegalArgumentException();
            position += literal.length();
        }

        private void requireDigits() {
            int start = position;
            while (position < input.length() && Character.isDigit(input.charAt(position))) position++;
            if (start == position) throw new IllegalArgumentException();
        }

        private void skipWhitespace() {
            while (position < input.length() && Character.isWhitespace(input.charAt(position))) position++;
        }

        private boolean consume(char expected) {
            if (position < input.length() && input.charAt(position) == expected) {
                position++;
                return true;
            }
            return false;
        }

        private void require(char expected) {
            if (!consume(expected)) throw new IllegalArgumentException();
        }
    }
}
