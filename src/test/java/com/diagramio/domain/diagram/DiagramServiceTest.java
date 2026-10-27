package com.diagramio.domain.diagram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.diagramio.domain.access.Caller;
import com.diagramio.domain.access.ShareRole;
import com.diagramio.domain.exception.InvalidInputException;
import com.diagramio.domain.exception.OptimisticLockConflictException;
import com.diagramio.domain.exception.UnauthorizedDiagramAccessException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DiagramServiceTest {

    @Mock
    private DiagramRepository repository;

    private DiagramService service;

    @BeforeEach
    void setUp() {
        service = new DiagramService(repository);
    }

    @Test
    void createDiagramUsesDefaultDataAndPrivateVisibility() {
        when(repository.save(any(Diagram.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Diagram diagram = new Diagram();
        diagram.setTitle("Orders");
        diagram.setDiagramType("ERD");
        diagram.setData(null);
        diagram.setPublic(true);

        Diagram created = service.createDiagram(diagram);

        assertEquals(DiagramService.DEFAULT_DATA, created.getData());
        assertFalse(created.isPublic());
        assertEquals(1, created.getVersion());
        verify(repository).save(diagram);
    }

    @Test
    void updateDiagramSavesWithCheckedVersionWhenRequestVersionMatches() {
        UUID id = UUID.randomUUID();
        Diagram current = diagram(id, 3);
        when(repository.findById(id)).thenReturn(java.util.Optional.of(current));
        when(repository.save(any(Diagram.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Diagram updated = service.updateDiagram(
                id, "Updated", "UML", "{\"nodes\": [], \"edges\": []}", false,
                Caller.user(current.getOwnerId()), 3);

        // Domain giữ nguyên version đã đối chiếu; @Version ở tầng persistence tăng lên 4 khi ghi
        assertEquals(3, updated.getVersion());
        assertEquals("Updated", updated.getTitle());
        verify(repository).save(current);
    }

    @Test
    void updateDiagramRejectsStaleVersion() {
        UUID id = UUID.randomUUID();
        Diagram current = diagram(id, 3);
        when(repository.findById(id)).thenReturn(java.util.Optional.of(current));

        assertThrows(OptimisticLockConflictException.class, () -> service.updateDiagram(
                id, "Updated", "UML", "{\"nodes\": [], \"edges\": []}", false,
                Caller.user(current.getOwnerId()), 2));

        verify(repository, never()).save(any(Diagram.class));
    }

    // ---- cấu trúc data ----

    @Test
    void updateDiagramRejectsDataWithoutNodesAndEdgesArrays() {
        UUID id = UUID.randomUUID();
        Diagram current = diagram(id, 1);
        when(repository.findById(id)).thenReturn(java.util.Optional.of(current));
        Caller owner = Caller.user(current.getOwnerId());

        for (String data : new String[] {"{\"foo\": 1}", "{\"nodes\": \"abc\", \"edges\": []}",
                "{\"nodes\": []}", "\"{\\\"nodes\\\": []}\"", "[]", "not json"}) {
            InvalidInputException ex = assertThrows(InvalidInputException.class,
                    () -> service.updateDiagram(id, "Orders", "ERD", data, false, owner, 1), data);
            assertTrue(ex.getMessage().startsWith("data must be"), data);
        }
        verify(repository, never()).save(any(Diagram.class));
    }

    // ---- phân trang ----

    @Test
    void pageSizeIsLimited() {
        UUID ownerId = UUID.randomUUID();
        assertThrows(InvalidInputException.class,
                () -> service.getDiagramsByOwner(ownerId, null, null, 0, DiagramService.MAX_PAGE_SIZE + 1));
        assertThrows(InvalidInputException.class, () -> service.getPublicDiagrams(0, 0));
        assertThrows(InvalidInputException.class, () -> service.getPublicDiagrams(-1, 20));
    }

    // ---- phân quyền sửa ----

    @Test
    void updateDiagramRejectsStranger() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(java.util.Optional.of(diagram(id, 1)));

        assertThrows(UnauthorizedDiagramAccessException.class, () -> service.updateDiagram(
                id, "Hacked", "ERD", DiagramService.DEFAULT_DATA, false, Caller.user(UUID.randomUUID()), 1));

        verify(repository, never()).save(any(Diagram.class));
    }

    @Test
    void updateDiagramRejectsViewShareToken() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(java.util.Optional.of(diagram(id, 1)));

        assertThrows(UnauthorizedDiagramAccessException.class, () -> service.updateDiagram(
                id, "Updated", "ERD", DiagramService.DEFAULT_DATA, false,
                Caller.shareToken(id, ShareRole.VIEW), 1));

        verify(repository, never()).save(any(Diagram.class));
    }

    @Test
    void editShareTokenCanUpdateButCannotChangeVisibility() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(java.util.Optional.of(diagram(id, 1)));
        when(repository.save(any(Diagram.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Diagram updated = service.updateDiagram(id, "Updated", "ERD", DiagramService.DEFAULT_DATA, true,
                Caller.shareToken(id, ShareRole.EDIT), 1);

        assertEquals("Updated", updated.getTitle());
        assertFalse(updated.isPublic());       // chỉ owner được công khai sơ đồ
        assertNull(updated.getUpdatedBy());    // sửa qua link, không đăng nhập
    }

    @Test
    void loggedInEditorIsRecordedAsUpdatedBy() {
        UUID id = UUID.randomUUID();
        UUID editorId = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(java.util.Optional.of(diagram(id, 1)));
        when(repository.save(any(Diagram.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Diagram updated = service.updateDiagram(id, "Updated", "ERD", DiagramService.DEFAULT_DATA, false,
                new Caller(editorId, id, ShareRole.EDIT), 1);

        assertEquals(editorId, updated.getUpdatedBy());
    }

    @Test
    void ownerCanMakeDiagramPublic() {
        UUID id = UUID.randomUUID();
        Diagram current = diagram(id, 1);
        when(repository.findById(id)).thenReturn(java.util.Optional.of(current));
        when(repository.save(any(Diagram.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Diagram updated = service.updateDiagram(id, "Orders", "ERD", DiagramService.DEFAULT_DATA, true,
                Caller.user(current.getOwnerId()), 1);

        assertTrue(updated.isPublic());
        assertEquals(current.getOwnerId(), updated.getUpdatedBy());
    }

    // ---- phân quyền xem ----

    @Test
    void getDiagramByIdRejectsAnonymousForPrivateDiagram() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(java.util.Optional.of(diagram(id, 1)));

        assertThrows(UnauthorizedDiagramAccessException.class,
                () -> service.getDiagramById(id, Caller.anonymous()));
    }

    @Test
    void getDiagramByIdAllowsViewShareTokenAndPublicDiagram() {
        UUID id = UUID.randomUUID();
        Diagram diagram = diagram(id, 1);
        when(repository.findById(id)).thenReturn(java.util.Optional.of(diagram));

        assertEquals(diagram, service.getDiagramById(id, Caller.shareToken(id, ShareRole.VIEW)));

        diagram.setPublic(true);
        assertEquals(diagram, service.getDiagramById(id, Caller.anonymous()));
    }

    @Test
    void deleteDiagramRejectsNonOwner() {
        UUID id = UUID.randomUUID();
        Diagram diagram = diagram(id, 1);
        when(repository.findById(id)).thenReturn(java.util.Optional.of(diagram));

        assertThrows(UnauthorizedDiagramAccessException.class,
                () -> service.deleteDiagram(id, Caller.user(UUID.randomUUID())));
        // link edit được sửa nhưng không được xóa
        assertThrows(UnauthorizedDiagramAccessException.class,
                () -> service.deleteDiagram(id, Caller.shareToken(id, ShareRole.EDIT)));

        verify(repository, never()).deleteById(id);
    }

    @Test
    void ownerCanDeleteDiagram() {
        UUID id = UUID.randomUUID();
        Diagram diagram = diagram(id, 1);
        when(repository.findById(id)).thenReturn(java.util.Optional.of(diagram));

        service.deleteDiagram(id, Caller.user(diagram.getOwnerId()));

        verify(repository).deleteById(id);
    }

    private Diagram diagram(UUID id, int version) {
        return new Diagram(id, "Orders", "ERD", DiagramService.DEFAULT_DATA, version,
                false, UUID.randomUUID(), null, null, null);
    }
}
