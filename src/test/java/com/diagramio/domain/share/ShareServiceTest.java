package com.diagramio.domain.share;

import com.diagramio.domain.access.Caller;
import com.diagramio.domain.access.ShareRole;
import com.diagramio.domain.exception.ForbiddenException;
import com.diagramio.domain.exception.NotFoundException;
import com.diagramio.domain.diagram.Diagram;
import com.diagramio.domain.diagram.DiagramRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShareServiceTest {

    @Mock
    private DiagramShareRepository shareRepository;

    @Mock
    private DiagramRepository diagramRepository;

    private ShareService shareService;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID otherUserId = UUID.randomUUID();
    private final UUID diagramId = UUID.randomUUID();

    private Diagram sampleDiagram;

    @BeforeEach
    void setUp() {
        shareService = new ShareService(shareRepository, diagramRepository);
        sampleDiagram = new Diagram(
                diagramId,
                "Sơ đồ lớp BTL",
                "UML",
                "{\"nodes\":[], \"edges\":[]}",
                1,
                false,
                ownerId,
                ownerId,
                Instant.now(),
                Instant.now()
        );
    }

    @Test
    @DisplayName("Chủ sở hữu có thể lấy danh sách liên kết chia sẻ")
    void getShares_OwnerSuccess() {
        when(diagramRepository.findById(diagramId)).thenReturn(Optional.of(sampleDiagram));
        DiagramShare share = new DiagramShare(UUID.randomUUID(), diagramId, "token123", ShareRole.VIEW, Instant.now());
        when(shareRepository.findByDiagramId(diagramId)).thenReturn(List.of(share));

        List<DiagramShare> result = shareService.getShares(diagramId, Caller.user(ownerId));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getShareToken()).isEqualTo("token123");
    }

    @Test
    @DisplayName("Người dùng khác cố lấy danh sách chia sẻ sẽ bị 403 Forbidden")
    void getShares_ForbiddenForNonOwner() {
        when(diagramRepository.findById(diagramId)).thenReturn(Optional.of(sampleDiagram));

        assertThatThrownBy(() -> shareService.getShares(diagramId, Caller.user(otherUserId)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Only the diagram owner");
    }

    @Test
    @DisplayName("Người cầm link edit vẫn không được quản lý liên kết (chỉ owner)")
    void createShare_ForbiddenForEditShareToken() {
        when(diagramRepository.findById(diagramId)).thenReturn(Optional.of(sampleDiagram));

        assertThatThrownBy(() -> shareService.createShare(diagramId, ShareRole.EDIT,
                Caller.shareToken(diagramId, ShareRole.EDIT)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("Sơ đồ không tồn tại sẽ bị 404 NotFound")
    void getShares_DiagramNotFound() {
        when(diagramRepository.findById(diagramId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shareService.getShares(diagramId, Caller.user(ownerId)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("Chủ sở hữu tạo liên kết chia sẻ thành công với quyền view")
    void createShare_Success() {
        when(diagramRepository.findById(diagramId)).thenReturn(Optional.of(sampleDiagram));
        when(shareRepository.save(any(DiagramShare.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DiagramShare created = shareService.createShare(diagramId, ShareRole.VIEW, Caller.user(ownerId));

        assertThat(created).isNotNull();
        assertThat(created.getDiagramId()).isEqualTo(diagramId);
        assertThat(created.getRole()).isEqualTo(ShareRole.VIEW);
        assertThat(created.getShareToken()).isNotBlank();
        verify(shareRepository).save(any(DiagramShare.class));
    }


    @Test
    @DisplayName("Cập nhật quyền liên kết chia sẻ thành công (view -> edit)")
    void updateShareRole_Success() {
        UUID shareId = UUID.randomUUID();
        DiagramShare oldShare = new DiagramShare(shareId, diagramId, "token123", ShareRole.VIEW, Instant.now());

        when(diagramRepository.findById(diagramId)).thenReturn(Optional.of(sampleDiagram));
        when(shareRepository.findById(shareId)).thenReturn(Optional.of(oldShare));
        when(shareRepository.save(any(DiagramShare.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DiagramShare updated = shareService.updateShareRole(diagramId, shareId, ShareRole.EDIT, Caller.user(ownerId));

        assertThat(updated.getRole()).isEqualTo(ShareRole.EDIT);
        assertThat(updated.getShareToken()).isEqualTo("token123");
    }

    @Test
    @DisplayName("Thu hồi liên kết chia sẻ thành công")
    void revokeShare_Success() {
        UUID shareId = UUID.randomUUID();
        DiagramShare oldShare = new DiagramShare(shareId, diagramId, "token123", ShareRole.VIEW, Instant.now());

        when(diagramRepository.findById(diagramId)).thenReturn(Optional.of(sampleDiagram));
        when(shareRepository.findById(shareId)).thenReturn(Optional.of(oldShare));

        shareService.revokeShare(diagramId, shareId, Caller.user(ownerId));

        verify(shareRepository).deleteById(shareId);
    }

    @Test
    @DisplayName("Truy xuất cấu trúc sơ đồ qua token hợp lệ (Public)")
    void getDiagramByShareToken_Success() {
        String token = "valid_token_xyz";
        DiagramShare share = new DiagramShare(UUID.randomUUID(), diagramId, token, ShareRole.EDIT, Instant.now());

        when(shareRepository.findByShareToken(token)).thenReturn(Optional.of(share));
        when(diagramRepository.findById(diagramId)).thenReturn(Optional.of(sampleDiagram));

        SharedDiagramView view = shareService.getDiagramByShareToken(token);

        assertThat(view).isNotNull();
        assertThat(view.getDiagramId()).isEqualTo(diagramId);
        assertThat(view.getTitle()).isEqualTo("Sơ đồ lớp BTL");
        assertThat(view.getRole()).isEqualTo(ShareRole.EDIT);
    }

    @Test
    @DisplayName("Truy xuất sơ đồ bằng token không tồn tại sẽ bị 404 NotFound")
    void getDiagramByShareToken_NotFound() {
        when(shareRepository.findByShareToken("invalid_token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shareService.getDiagramByShareToken("invalid_token"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    @DisplayName("Token X-Share-Token hợp lệ được đổi thành Caller có quyền trên đúng sơ đồ")
    void resolveShareToken_ValidToken() {
        DiagramShare share = new DiagramShare(UUID.randomUUID(), diagramId, "edit_token", ShareRole.EDIT, Instant.now());
        when(shareRepository.findByShareToken("edit_token")).thenReturn(Optional.of(share));

        assertThat(shareService.resolveShareToken(" edit_token "))
                .contains(Caller.shareToken(diagramId, ShareRole.EDIT));
    }

    @Test
    @DisplayName("Token không tồn tại hoặc đã thu hồi thì không có Caller")
    void resolveShareToken_UnknownOrBlank() {
        when(shareRepository.findByShareToken("revoked")).thenReturn(Optional.empty());

        assertThat(shareService.resolveShareToken("revoked")).isEmpty();
        assertThat(shareService.resolveShareToken("  ")).isEmpty();
        assertThat(shareService.resolveShareToken(null)).isEmpty();
    }
}
