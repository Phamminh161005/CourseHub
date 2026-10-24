package com.diagramio.domain.version;

import com.diagramio.domain.TransactionRunner;
import com.diagramio.domain.access.Caller;
import com.diagramio.domain.diagram.Diagram;
import com.diagramio.domain.diagram.DiagramRepository;
import com.diagramio.domain.exception.DiagramNotFoundException;
import com.diagramio.domain.exception.ForbiddenException;
import com.diagramio.domain.exception.InvalidInputException;
import com.diagramio.domain.exception.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Lưu phiên bản (snapshot) và khôi phục sơ đồ về một phiên bản cũ. */
public class DiagramVersionService {

    public static final int MAX_NOTE_LENGTH = 255;

    private final DiagramRepository diagramRepository;
    private final DiagramVersionRepository versionRepository;
    private final TransactionRunner transactionRunner;
    private final Clock clock;

    public DiagramVersionService(DiagramRepository diagramRepository,
                                 DiagramVersionRepository versionRepository,
                                 TransactionRunner transactionRunner,
                                 Clock clock) {
        this.diagramRepository = diagramRepository;
        this.versionRepository = versionRepository;
        this.transactionRunner = transactionRunner;
        this.clock = clock;
    }

    public List<DiagramVersion> listVersions(UUID diagramId, Caller caller) {
        Diagram diagram = findDiagram(diagramId);
        requireHistoryAccess(diagram, caller);
        return versionRepository.findByDiagramId(diagramId);
    }

    public DiagramVersion getVersion(UUID diagramId, UUID versionId, Caller caller) {
        Diagram diagram = findDiagram(diagramId);
        requireHistoryAccess(diagram, caller);
        return findVersion(diagramId, versionId);
    }

    public DiagramVersion createVersion(UUID diagramId, String note, Caller caller) {
        if (note != null && note.length() > MAX_NOTE_LENGTH) {
            throw new InvalidInputException("note must not exceed " + MAX_NOTE_LENGTH + " characters");
        }
        return transactionRunner.inTransaction(() -> {
            Diagram diagram = findDiagram(diagramId);
            requireEditAccess(diagram, caller);
            return snapshot(diagram, note, caller);
        });
    }

    /**
     * Sao lưu trạng thái hiện tại thành một snapshot, rồi chép dữ liệu của phiên bản được chọn
     * vào sơ đồ. Cả hai bước nằm trong cùng một transaction.
     */
    public RestoreResult restore(UUID diagramId, UUID versionId, Caller caller) {
        return transactionRunner.inTransaction(() -> {
            Diagram diagram = findDiagram(diagramId);
            requireEditAccess(diagram, caller);
            DiagramVersion target = findVersion(diagramId, versionId);

            DiagramVersion backup = snapshot(diagram,
                    "Auto backup before restoring version " + target.versionNumber(), caller);

            diagram.setData(target.data());
            diagram.setVersion(diagram.getVersion() + 1);
            diagram.setUpdatedBy(caller.userId());
            diagram.setUpdatedAt(Instant.now(clock));
            Diagram restored = diagramRepository.save(diagram);
            return new RestoreResult(restored, backup);
        });
    }

    private DiagramVersion snapshot(Diagram diagram, String note, Caller caller) {
        int nextNumber = versionRepository.maxVersionNumber(diagram.getId()) + 1;
        DiagramVersion version = new DiagramVersion(UUID.randomUUID(), diagram.getId(), nextNumber,
                diagram.getData(), note, caller.userId(), Instant.now(clock));
        return versionRepository.save(version);
    }

    private Diagram findDiagram(UUID diagramId) {
        return diagramRepository.findById(diagramId)
                .orElseThrow(() -> new DiagramNotFoundException("Diagram not found: " + diagramId));
    }

    private DiagramVersion findVersion(UUID diagramId, UUID versionId) {
        return versionRepository.findByIdAndDiagramId(versionId, diagramId)
                .orElseThrow(() -> new NotFoundException("Version not found: " + versionId));
    }

    private void requireHistoryAccess(Diagram diagram, Caller caller) {
        if (!caller.canViewHistory(diagram)) {
            throw new ForbiddenException("You do not have access to this diagram's version history");
        }
    }

    private void requireEditAccess(Diagram diagram, Caller caller) {
        if (!caller.canEdit(diagram)) {
            throw new ForbiddenException("Only the owner or an editor can save or restore versions");
        }
    }
}
