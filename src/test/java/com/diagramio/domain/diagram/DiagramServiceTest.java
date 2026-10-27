package com.diagramio.domain.diagram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
                current.getOwnerId(), 3);

        // Domain giữ nguyên version đã đối chiếu; @Version ở tầng persistence tăng lên 4 khi ghi
        assertEquals(3, updated.getVersion());
        assertEquals("Updated", updated.getTitle());
        verify(repository).save(current);
    }

    @Test
    void updateDiagramRejectsStaleVersion() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(java.util.Optional.of(diagram(id, 3)));

        assertThrows(OptimisticLockConflictException.class, () -> service.updateDiagram(
                id, "Updated", "UML", "{\"nodes\": [], \"edges\": []}", false,
                UUID.randomUUID(), 2));

        verify(repository, never()).save(any(Diagram.class));
    }

    @Test
    void deleteDiagramRejectsNonOwner() {
        UUID id = UUID.randomUUID();
        Diagram diagram = diagram(id, 1);
        when(repository.findById(id)).thenReturn(java.util.Optional.of(diagram));

        assertThrows(UnauthorizedDiagramAccessException.class,
                () -> service.deleteDiagram(id, UUID.randomUUID()));

        verify(repository, never()).deleteById(id);
    }

    private Diagram diagram(UUID id, int version) {
        return new Diagram(id, "Orders", "ERD", DiagramService.DEFAULT_DATA, version,
                false, UUID.randomUUID(), null, null, null);
    }
}
