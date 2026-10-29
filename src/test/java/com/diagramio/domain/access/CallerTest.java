package com.diagramio.domain.access;

import static org.assertj.core.api.Assertions.assertThat;

import com.diagramio.domain.diagram.Diagram;
import com.diagramio.domain.diagram.DiagramData;
import com.diagramio.domain.diagram.DiagramType;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CallerTest {

    private final UUID ownerId = UUID.randomUUID();
    private final Diagram diagram = new Diagram(UUID.randomUUID(), "ERD", DiagramType.ERD, DiagramData.fromStorage("{}"), 1, false, ownerId,
            null, null, null);

    @Test
    void ownerCanEditAndViewHistory() {
        Caller owner = Caller.user(ownerId);
        assertThat(owner.canEdit(diagram)).isTrue();
        assertThat(owner.canViewHistory(diagram)).isTrue();
    }

    @Test
    void editShareCanEditAndViewHistory() {
        Caller editor = Caller.shareToken(diagram.getId(), ShareRole.EDIT);
        assertThat(editor.canEdit(diagram)).isTrue();
        assertThat(editor.canViewHistory(diagram)).isTrue();
    }

    @Test
    void viewShareCanOnlyViewHistory() {
        Caller viewer = Caller.shareToken(diagram.getId(), ShareRole.VIEW);
        assertThat(viewer.canEdit(diagram)).isFalse();
        assertThat(viewer.canViewHistory(diagram)).isTrue();
    }

    @Test
    void shareOfAnotherDiagramGivesNoAccess() {
        Caller editor = Caller.shareToken(UUID.randomUUID(), ShareRole.EDIT);
        assertThat(editor.canEdit(diagram)).isFalse();
        assertThat(editor.canViewHistory(diagram)).isFalse();
    }

    @Test
    void strangerHasNoAccess() {
        Caller stranger = Caller.user(UUID.randomUUID());
        assertThat(stranger.canEdit(diagram)).isFalse();
        assertThat(stranger.canViewHistory(diagram)).isFalse();
    }

    @Test
    void publicDiagramDoesNotExposeHistoryToStrangers() {
        diagram.setPublic(true);
        Caller stranger = Caller.user(UUID.randomUUID());
        assertThat(stranger.canViewHistory(diagram)).isFalse();
        assertThat(stranger.canEdit(diagram)).isFalse();
    }

    @Test
    void canViewPrivateDiagramOnlyWithAccess() {
        assertThat(Caller.anonymous().canView(diagram)).isFalse();
        assertThat(Caller.user(UUID.randomUUID()).canView(diagram)).isFalse();
        assertThat(Caller.user(ownerId).canView(diagram)).isTrue();
        assertThat(Caller.shareToken(diagram.getId(), ShareRole.VIEW).canView(diagram)).isTrue();
    }

    @Test
    void anyoneCanViewPublicDiagram() {
        diagram.setPublic(true);
        assertThat(Caller.anonymous().canView(diagram)).isTrue();
    }

    @Test
    void ownerOpeningOwnViewLinkKeepsOwnerRights() {
        Caller ownerWithViewLink = new Caller(ownerId, diagram.getId(), ShareRole.VIEW);
        assertThat(ownerWithViewLink.canEdit(diagram)).isTrue();
    }
}
