package com.diagramio.domain.share;

import com.diagramio.domain.exception.ForbiddenException;
import com.diagramio.domain.exception.InvalidInputException;
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
        DiagramShare share = new DiagramShare(UUID.randomUUID(), diagramId, "token123", "view", Instant.now());
        when(shareRepository.findByDiagramId(diagramId)).thenReturn(List.of(share));

        List<DiagramShare> result = shareService.getShares(diagramId, ownerId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getShareToken()).isEqualTo("token123");
    }

    @Test
    @DisplayName("Người dùng khác cố lấy danh sách chia sẻ sẽ bị 403 Forbidden")
    void getShares_ForbiddenForNonOwner() {
        when(diagramRepository.findById(diagramId)).thenReturn(Optional.of(sampleDiagram));

        assertThatThrownBy(() -> shareService.getShares(diagramId, otherUserId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Chỉ chủ sở hữu");
    }

    @Test
    @DisplayName("Sơ đồ không tồn tại sẽ bị 404 NotFound")
    void getShares_DiagramNotFound() {
        when(diagramRepository.findById(diagramId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shareService.getShares(diagramId, ownerId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("Chủ sở hữu tạo liên kết chia sẻ thành công với quyền view")
    void createShare_Success() {
        when(diagramRepository.findById(diagramId)).thenReturn(Optional.of(sampleDiagram));
        when(shareRepository.save(any(DiagramShare.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DiagramShare created = shareService.createShare(diagramId, "view", ownerId);

        assertThat(created).isNotNull();
        assertThat(created.getDiagramId()).isEqualTo(diagramId);
        assertThat(created.getRole()).isEqualTo("view");
        assertThat(created.getShareToken()).isNotBlank();
        verify(shareRepository).save(any(DiagramShare.class));
    }

    @Test
    @DisplayName("Tạo liên kết với quyền không hợp lệ sẽ bị InvalidInputException")
    void createShare_InvalidRole() {
        when(diagramRepository.findById(diagramId)).thenReturn(Optional.of(sampleDiagram));

        assertThatThrownBy(() -> shareService.createShare(diagramId, "admin", ownerId))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("chỉ có thể là 'view' hoặc 'edit'");
    }

    @Test
    @DisplayName("Cập nhật quyền liên kết chia sẻ thành công (view -> edit)")
    void updateShareRole_Success() {
        UUID shareId = UUID.randomUUID();
        DiagramShare oldShare = new DiagramShare(shareId, diagramId, "token123", "view", Instant.now());

        when(diagramRepository.findById(diagramId)).thenReturn(Optional.of(sampleDiagram));
        when(shareRepository.findById(shareId)).thenReturn(Optional.of(oldShare));
        when(shareRepository.save(any(DiagramShare.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DiagramShare updated = shareService.updateShareRole(diagramId, shareId, "edit", ownerId);

        assertThat(updated.getRole()).isEqualTo("edit");
        assertThat(updated.getShareToken()).isEqualTo("token123");
    }

    @Test
    @DisplayName("Thu hồi liên kết chia sẻ thành công")
    void revokeShare_Success() {
        UUID shareId = UUID.randomUUID();
        DiagramShare oldShare = new DiagramShare(shareId, diagramId, "token123", "view", Instant.now());

        when(diagramRepository.findById(diagramId)).thenReturn(Optional.of(sampleDiagram));
        when(shareRepository.findById(shareId)).thenReturn(Optional.of(oldShare));

        shareService.revokeShare(diagramId, shareId, ownerId);

        verify(shareRepository).deleteById(shareId);
    }

    @Test
    @DisplayName("Truy xuất cấu trúc sơ đồ qua token hợp lệ (Public)")
    void getDiagramByShareToken_Success() {
        String token = "valid_token_xyz";
        DiagramShare share = new DiagramShare(UUID.randomUUID(), diagramId, token, "edit", Instant.now());

        when(shareRepository.findByShareToken(token)).thenReturn(Optional.of(share));
        when(diagramRepository.findById(diagramId)).thenReturn(Optional.of(sampleDiagram));

        SharedDiagramView view = shareService.getDiagramByShareToken(token);

        assertThat(view).isNotNull();
        assertThat(view.getDiagramId()).isEqualTo(diagramId);
        assertThat(view.getTitle()).isEqualTo("Sơ đồ lớp BTL");
        assertThat(view.getRole()).isEqualTo("edit");
    }

    @Test
    @DisplayName("Truy xuất sơ đồ bằng token không tồn tại sẽ bị 404 NotFound")
    void getDiagramByShareToken_NotFound() {
        when(shareRepository.findByShareToken("invalid_token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shareService.getDiagramByShareToken("invalid_token"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("không tồn tại");
    }
}
