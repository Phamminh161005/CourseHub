package com.diagramio.domain.share;

import com.diagramio.domain.access.Caller;
import com.diagramio.domain.access.DiagramAccess;
import com.diagramio.domain.access.Permission;
import com.diagramio.domain.access.ShareRole;
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
 * Quản lý liên kết chỉ dành cho chủ sở hữu (Permission.MANAGE qua DiagramAccess).
 */
public class ShareService {

    private final DiagramShareRepository shareRepository;
    private final DiagramRepository diagramRepository;
    private final DiagramAccess access;
    private final SecureRandom secureRandom = new SecureRandom();

    public ShareService(DiagramShareRepository shareRepository, DiagramRepository diagramRepository) {
        this.shareRepository = Objects.requireNonNull(shareRepository, "shareRepository must not be null");
        this.diagramRepository = Objects.requireNonNull(diagramRepository, "diagramRepository must not be null");
        this.access = new DiagramAccess(diagramRepository);
    }

    /**
     * Lấy danh sách liên kết chia sẻ của sơ đồ. Chỉ chủ sở hữu mới có quyền xem.
     */
    public List<DiagramShare> getShares(UUID diagramId, Caller caller) {
        Diagram diagram = access.require(diagramId, caller, Permission.MANAGE);
        return shareRepository.findByDiagramId(diagram.getId());
    }

    /**
     * Tạo liên kết chia sẻ mới (role: view hoặc edit). Chỉ chủ sở hữu mới có quyền tạo.
     */
    public DiagramShare createShare(UUID diagramId, ShareRole role, Caller caller) {
        Diagram diagram = access.require(diagramId, caller, Permission.MANAGE);

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
     * Dùng một câu UPDATE có điều kiện thay vì đọc-sửa-lưu: nếu link vừa bị thu hồi ở request khác
     * thì trả 404, không tạo lại link đã thu hồi.
     */
    public DiagramShare updateShareRole(UUID diagramId, UUID shareId, ShareRole newRole, Caller caller) {
        Diagram diagram = access.require(diagramId, caller, Permission.MANAGE);

        if (!shareRepository.updateRole(shareId, diagram.getId(), newRole)) {
            throw new NotFoundException("Share link not found");
        }
        return findShareOfDiagram(diagram, shareId);
    }

    /**
     * Thu hồi liên kết chia sẻ. Chỉ chủ sở hữu mới có quyền thu hồi.
     */
    public void revokeShare(UUID diagramId, UUID shareId, Caller caller) {
        Diagram diagram = access.require(diagramId, caller, Permission.MANAGE);

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
