package com.diagramio.domain.diagram;

import com.diagramio.domain.access.Caller;
import com.diagramio.domain.access.DiagramAccess;
import com.diagramio.domain.access.Permission;
import com.diagramio.domain.exception.InvalidInputException;
import com.diagramio.domain.exception.OptimisticLockConflictException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Domain rules for diagram management. This class intentionally has no framework dependency. */
public class DiagramService {

    public static final int MAX_PAGE_SIZE = 100;

    private final DiagramRepository repository;
    private final DiagramAccess access;

    public DiagramService(DiagramRepository repository) {
        this.repository = repository;
        this.access = new DiagramAccess(repository);
    }

    public Diagram createDiagram(Diagram diagram) {
        if (diagram == null) {
            throw new InvalidInputException("Diagram is required");
        }
        requireType(diagram.getDiagramType());
        if (diagram.getData() == null) {
            diagram.setData(DiagramData.EMPTY);
        }

        Instant now = Instant.now();
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
    public Diagram updateDiagram(UUID id, String title, DiagramType diagramType, DiagramData data,
                                 boolean isPublic, Caller caller, Integer requestVersion) {
        if (id == null) {
            throw new InvalidInputException("Diagram id is required");
        }
        Diagram current = access.require(id, caller, Permission.EDIT);
        checkVersion(id, requestVersion, current.getVersion());
        requireType(diagramType);
        if (data == null) {
            throw new InvalidInputException("data must be valid JSON");
        }

        Instant now = Instant.now();
        current.updateMetadata(title, diagramType, caller.userId(), now);
        current.replaceContent(data, caller.userId(), now);
        // Chỉ owner đổi được trạng thái công khai; người sửa qua link gửi giá trị khác thì bỏ qua
        if (Permission.MANAGE.isGrantedTo(caller, current)) {
            current.setPublic(isPublic);
        }
        return repository.save(current);
    }

    public void deleteDiagram(UUID id, Caller caller) {
        access.require(id, caller, Permission.MANAGE);
        repository.deleteById(id);
    }

    public Diagram getDiagramById(UUID id, Caller caller) {
        return access.require(id, caller, Permission.VIEW);
    }

    /** diagramType null = mọi loại. */
    public List<Diagram> getDiagramsByOwner(UUID ownerId, DiagramType diagramType, String title,
                                            int page, int size) {
        validatePage(page, size);
        return repository.findByOwnerId(ownerId, diagramType, normalizeFilter(title), page, size);
    }

    public long countDiagramsByOwner(UUID ownerId, DiagramType diagramType, String title) {
        return repository.countByOwnerId(ownerId, diagramType, normalizeFilter(title));
    }

    public List<Diagram> getPublicDiagrams(int page, int size) {
        validatePage(page, size);
        return repository.findPublicDiagrams(page, size);
    }

    public long countPublicDiagrams() {
        return repository.countPublicDiagrams();
    }

    private void checkVersion(UUID id, Integer requestedVersion, Integer currentVersion) {
        if (requestedVersion == null || currentVersion == null || !requestedVersion.equals(currentVersion)) {
            throw new OptimisticLockConflictException(id, requestedVersion, currentVersion);
        }
    }

    private void requireType(DiagramType diagramType) {
        if (diagramType == null) {
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

}
