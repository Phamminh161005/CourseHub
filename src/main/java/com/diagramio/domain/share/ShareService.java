package com.diagramio.domain.share;

import com.diagramio.domain.access.Caller;
import com.diagramio.domain.access.ShareRole;
import com.diagramio.domain.exception.ForbiddenException;
import com.diagramio.domain.exception.NotFoundException;
import com.diagramio.domain.diagram.Diagram;
import com.diagramio.domain.diagram.DiagramRepository;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Tầng nghiệp vụ xử lý Chia sẻ & Phân quyền sơ đồ.
 * Thuần Java (Plain Java), không dùng bất kỳ annotation nào của Spring hay JPA.
 * Quản lý liên kết chỉ dành cho chủ sở hữu (Caller.canManage).
 */
public class ShareService {

    private final DiagramShareRepository shareRepository;
    private final DiagramRepository diagramRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public ShareService(DiagramShareRepository shareRepository, DiagramRepository diagramRepository) {
        this.shareRepository = Objects.requireNonNull(shareRepository, "shareRepository must not be null");
        this.diagramRepository = Objects.requireNonNull(diagramRepository, "diagramRepository must not be null");
    }

    /**
     * Lấy danh sách liên kết chia sẻ của sơ đồ. Chỉ chủ sở hữu mới có quyền xem.
     */
    public List<DiagramShare> getShares(UUID diagramId, Caller caller) {
        Diagram diagram = getDiagramAndVerifyOwner(diagramId, caller,
                "Only the diagram owner can view its share links");
        return shareRepository.findByDiagramId(diagram.getId());
    }

    /**
     * Tạo liên kết chia sẻ mới (role: view hoặc edit). Chỉ chủ sở hữu mới có quyền tạo.
     */
    public DiagramShare createShare(UUID diagramId, ShareRole role, Caller caller) {
        Diagram diagram = getDiagramAndVerifyOwner(diagramId, caller,
                "Only the diagram owner can create share links");

        DiagramShare share = new DiagramShare(
                UUID.randomUUID(),
                diagram.getId(),
                generateUniqueToken(),
                role,
                Instant.now()
        );

        return shareRepository.save(share);
    }

    /**
     * Thay đổi mức độ quyền chia sẻ (view <-> edit). Chỉ chủ sở hữu mới có quyền đổi.
     */
    public DiagramShare updateShareRole(UUID diagramId, UUID shareId, ShareRole newRole, Caller caller) {
        Diagram diagram = getDiagramAndVerifyOwner(diagramId, caller,
                "Only the diagram owner can change share roles");

        DiagramShare existingShare = findShareOfDiagram(diagram, shareId);
        return shareRepository.save(existingShare.withRole(newRole));
    }

    /**
     * Thu hồi liên kết chia sẻ. Chỉ chủ sở hữu mới có quyền thu hồi.
     */
    public void revokeShare(UUID diagramId, UUID shareId, Caller caller) {
        Diagram diagram = getDiagramAndVerifyOwner(diagramId, caller,
                "Only the diagram owner can revoke share links");

        findShareOfDiagram(diagram, shareId);
        shareRepository.deleteById(shareId);
    }

    /**
     * Truy xuất thông tin và cấu trúc sơ đồ thông qua mã chia sẻ (public).
     */
    public SharedDiagramView getDiagramByShareToken(String token) {
        if (token == null || token.isBlank()) {
            throw new NotFoundException("Share token is invalid");
        }

        DiagramShare share = shareRepository.findByShareToken(token.trim())
                .orElseThrow(() -> new NotFoundException("Share token does not exist or has been revoked"));

        Diagram diagram = diagramRepository.findById(share.getDiagramId())
                .orElseThrow(() -> new NotFoundException("Shared diagram no longer exists"));

        return new SharedDiagramView(
                diagram.getId(),
                diagram.getTitle(),
                diagram.getDiagramType(),
                diagram.getData(),
                share.getRole(),
                diagram.getVersion(),
                diagram.isPublic(),
                diagram.getCreatedAt(),
                diagram.getUpdatedAt()
        );
    }

    /**
     * Đổi token chia sẻ (header X-Share-Token) thành Caller có quyền trên đúng sơ đồ đó.
     * Token không tồn tại hoặc đã thu hồi (bị xóa) thì trả rỗng.
     */
    public Optional<Caller> resolveShareToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return shareRepository.findByShareToken(token.trim())
                .map(share -> Caller.shareToken(share.getDiagramId(), share.getRole()));
    }

    private Diagram getDiagramAndVerifyOwner(UUID diagramId, Caller caller, String forbiddenMessage) {
        if (diagramId == null) {
            throw new NotFoundException("Diagram not found");
        }

        Diagram diagram = diagramRepository.findById(diagramId)
                .orElseThrow(() -> new NotFoundException("Diagram not found: " + diagramId));

        if (!caller.canManage(diagram)) {
            throw new ForbiddenException(forbiddenMessage);
        }

        return diagram;
    }

    private DiagramShare findShareOfDiagram(Diagram diagram, UUID shareId) {
        DiagramShare share = shareRepository.findById(shareId)
                .orElseThrow(() -> new NotFoundException("Share link not found"));
        if (!share.getDiagramId().equals(diagram.getId())) {
            throw new NotFoundException("Share link does not belong to this diagram");
        }
        return share;
    }

    private String generateUniqueToken() {
        byte[] randomBytes = new byte[24]; // 24 bytes = 48 hex characters
        String token;
        do {
            secureRandom.nextBytes(randomBytes);
            token = HexFormat.of().formatHex(randomBytes);
        } while (shareRepository.existsByShareToken(token));
        return token;
    }
}
