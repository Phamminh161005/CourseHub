package com.diagramio.domain.exception;

import java.util.UUID;

public class OptimisticLockConflictException extends ConflictException {

    private final UUID id;
    private final Integer requestedVersion;
    private final Integer currentVersion;

    public OptimisticLockConflictException(UUID id, Integer requestedVersion, Integer currentVersion) {
        super("Diagram version conflict: requested " + requestedVersion + ", current " + currentVersion);
        this.id = id;
        this.requestedVersion = requestedVersion;
        this.currentVersion = currentVersion;
    }

    public UUID getId() { return id; }
    public Integer getRequestedVersion() { return requestedVersion; }
    public Integer getCurrentVersion() { return currentVersion; }
}
