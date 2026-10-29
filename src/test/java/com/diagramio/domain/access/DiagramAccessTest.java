package com.diagramio.domain.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.diagramio.domain.diagram.Diagram;
import com.diagramio.domain.diagram.DiagramRepository;
import com.diagramio.domain.diagram.DiagramType;
import com.diagramio.domain.exception.DiagramNotFoundException;
import com.diagramio.domain.exception.ForbiddenException;
import com.diagramio.domain.exception.UnauthorizedException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DiagramAccessTest {

    private final DiagramRepository repository = mock(DiagramRepository.class);
    private final DiagramAccess access = new DiagramAccess(repository);

    private final UUID ownerId = UUID.randomUUID();
    private final UUID diagramId = UUID.randomUUID();
    private Diagram diagram;

    private Caller owner;
    private Caller editLink;
    private Caller viewLink;
    private Caller stranger;
    private Caller anonymous;
    private Caller linkOfOtherDiagram;

    @BeforeEach
    void setUp() {
        diagram = new Diagram(diagramId, "ERD", DiagramType.ERD, "{\"nodes\": [], \"edges\": []}", 1, false, ownerId,
                null, null, null);
        when(repository.findById(diagramId)).thenReturn(Optional.of(diagram));
        owner = Caller.user(ownerId);
        editLink = Caller.shareToken(diagramId, ShareRole.EDIT);
        viewLink = Caller.shareToken(diagramId, ShareRole.VIEW);
        stranger = Caller.user(UUID.randomUUID());
        anonymous = Caller.anonymous();
        linkOfOtherDiagram = Caller.shareToken(UUID.randomUUID(), ShareRole.EDIT);
    }

    @Test
    void ownerHasEveryPermission() {
        for (Permission permission : Permission.values()) {
            assertThat(access.require(diagramId, owner, permission)).isSameAs(diagram);
        }
    }

    @Test
    void editLinkCanViewAndEditButNotManage() {
        assertGranted(editLink, Permission.VIEW, Permission.VIEW_HISTORY, Permission.EDIT);
        assertForbidden(editLink, Permission.MANAGE);
    }

    @Test
    void viewLinkCanOnlyView() {
        assertGranted(viewLink, Permission.VIEW, Permission.VIEW_HISTORY);
        assertForbidden(viewLink, Permission.EDIT, Permission.MANAGE);
    }

    @Test
    void loggedInStrangerIsForbiddenOnPrivateDiagram() {
        assertForbidden(stranger, Permission.values());
    }

    @Test
    void anonymousIsUnauthorizedOnPrivateDiagram() {
        assertUnauthorized(anonymous, Permission.values());
    }

    @Test
    void linkOfAnotherDiagramCountsAsNoIdentity() {
        assertUnauthorized(linkOfOtherDiagram, Permission.values());
    }

    @Test
    void publicDiagramOpensContentButNotHistory() {
        diagram.setPublic(true);

        assertGranted(anonymous, Permission.VIEW);
        assertGranted(stranger, Permission.VIEW);
        assertUnauthorized(anonymous, Permission.VIEW_HISTORY, Permission.EDIT, Permission.MANAGE);
        assertForbidden(stranger, Permission.VIEW_HISTORY, Permission.EDIT, Permission.MANAGE);
    }

    @Test
    void ownerWithViewLinkKeepsOwnerRights() {
        assertGranted(new Caller(ownerId, diagramId, ShareRole.VIEW), Permission.values());
    }

    @Test
    void missingDiagramIs404ForEveryone() {
        UUID missing = UUID.randomUUID();
        when(repository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> access.require(missing, owner, Permission.VIEW))
                .isInstanceOf(DiagramNotFoundException.class);
        assertThatThrownBy(() -> access.require(missing, anonymous, Permission.VIEW))
                .isInstanceOf(DiagramNotFoundException.class);
        assertThatThrownBy(() -> access.require(null, owner, Permission.VIEW))
                .isInstanceOf(DiagramNotFoundException.class);
    }

    @Test
    void deniedMessageNamesThePermission() {
        assertThatThrownBy(() -> access.require(diagramId, viewLink, Permission.EDIT))
                .hasMessage(Permission.EDIT.deniedMessage());
    }

    private void assertGranted(Caller caller, Permission... permissions) {
        for (Permission permission : permissions) {
            assertThat(access.require(diagramId, caller, permission)).as(permission.name()).isSameAs(diagram);
        }
    }

    private void assertForbidden(Caller caller, Permission... permissions) {
        for (Permission permission : permissions) {
            assertThatThrownBy(() -> access.require(diagramId, caller, permission))
                    .as(permission.name()).isInstanceOf(ForbiddenException.class);
        }
    }

    private void assertUnauthorized(Caller caller, Permission... permissions) {
        for (Permission permission : permissions) {
            assertThatThrownBy(() -> access.require(diagramId, caller, permission))
                    .as(permission.name()).isInstanceOf(UnauthorizedException.class);
        }
    }
}
