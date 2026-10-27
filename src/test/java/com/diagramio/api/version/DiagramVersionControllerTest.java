package com.diagramio.api.version;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.diagramio.config.SecurityConfig;
import com.diagramio.domain.access.Caller;
import com.diagramio.domain.access.ShareRole;
import com.diagramio.domain.diagram.Diagram;
import com.diagramio.domain.exception.ConflictException;
import com.diagramio.domain.exception.ForbiddenException;
import com.diagramio.domain.exception.NotFoundException;
import com.diagramio.domain.version.DiagramVersion;
import com.diagramio.domain.version.DiagramVersionService;
import com.diagramio.domain.version.RestoreResult;
import com.diagramio.infrastructure.security.JwtTokenProvider;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DiagramVersionController.class)
@Import(SecurityConfig.class)
class DiagramVersionControllerTest {

    private static final String DATA = "{\"nodes\":[{\"id\":\"n1\"}],\"edges\":[]}";
    private static final Instant NOW = Instant.parse("2026-10-24T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DiagramVersionService versionService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider; // SecurityConfig cần; mặc định coi mọi token là không hợp lệ

    private final UUID ownerId = UUID.randomUUID();
    private final UUID diagramId = UUID.randomUUID();
    private final UUID versionId = UUID.randomUUID();
    private final String base = "/api/v1/diagrams/" + diagramId + "/versions";

    // ---------- 401 khi chưa đăng nhập ----------

    @Test
    void allEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get(base)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(base)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(base + "/" + versionId)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(base + "/" + versionId + "/restore")).andExpect(status().isUnauthorized());
        verify(versionService, never()).listVersions(any(), any());
    }

    // ---------- POST /versions ----------

    @Test
    void createReturns201WithLocationAndSnapshot() throws Exception {
        when(versionService.createVersion(diagramId, "Bản thiết kế ERD Pha 1", Caller.user(ownerId)))
                .thenReturn(version(versionId, 3));

        mockMvc.perform(post(base).with(user(ownerId.toString()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\": \"Bản thiết kế ERD Pha 1\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith(base + "/" + versionId)))
                .andExpect(jsonPath("$.id").value(versionId.toString()))
                .andExpect(jsonPath("$.diagramId").value(diagramId.toString()))
                .andExpect(jsonPath("$.versionNumber").value(3))
                .andExpect(jsonPath("$.note").value("note 3"))
                .andExpect(jsonPath("$.data.nodes[0].id").value("n1"))
                .andExpect(jsonPath("$.createdBy").value(ownerId.toString()));
    }

    @Test
    void createWithoutBodyUsesNullNote() throws Exception {
        when(versionService.createVersion(eq(diagramId), isNull(), any())).thenReturn(version(versionId, 1));

        mockMvc.perform(post(base).with(user(ownerId.toString())))
                .andExpect(status().isCreated());
    }

    @Test
    void createAsEditShareTokenPassesShareCaller() throws Exception {
        Caller editor = Caller.shareToken(diagramId, ShareRole.EDIT);
        when(versionService.createVersion(diagramId, null, editor)).thenReturn(version(versionId, 1));

        mockMvc.perform(post(base).with(authentication(new UsernamePasswordAuthenticationToken(editor, null, List.of()))))
                .andExpect(status().isCreated());
        verify(versionService).createVersion(diagramId, null, editor);
    }

    @Test
    void createWithTooLongNoteIs400() throws Exception {
        String note = "a".repeat(256);

        mockMvc.perform(post(base).with(user(ownerId.toString()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\": \"" + note + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value(base));
        verify(versionService, never()).createVersion(any(), any(), any());
    }

    @Test
    void createForbiddenIs403() throws Exception {
        when(versionService.createVersion(any(), any(), any())).thenThrow(new ForbiddenException("no edit"));

        mockMvc.perform(post(base).with(user(ownerId.toString())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("no edit"));
    }

    @Test
    void concurrentCreateIs409() throws Exception {
        when(versionService.createVersion(any(), any(), any())).thenThrow(new ConflictException("retry"));

        mockMvc.perform(post(base).with(user(ownerId.toString())))
                .andExpect(status().isConflict());
    }

    // ---------- GET /versions, GET /versions/{id} ----------

    @Test
    void listReturnsSummariesWithoutData() throws Exception {
        when(versionService.listVersions(diagramId, Caller.user(ownerId)))
                .thenReturn(List.of(version(UUID.randomUUID(), 2), version(versionId, 1)));

        mockMvc.perform(get(base).with(user(ownerId.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].versionNumber").value(2))
                .andExpect(jsonPath("$[1].id").value(versionId.toString()))
                .andExpect(jsonPath("$[0].data").doesNotExist());
    }

    @Test
    void getReturnsSnapshotDataAsJsonObject() throws Exception {
        when(versionService.getVersion(diagramId, versionId, Caller.user(ownerId))).thenReturn(version(versionId, 1));

        mockMvc.perform(get(base + "/" + versionId).with(user(ownerId.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.edges").isArray())
                .andExpect(jsonPath("$.data.nodes[0].id").value("n1"));
    }

    @Test
    void getMissingVersionIs404() throws Exception {
        when(versionService.getVersion(any(), any(), any())).thenThrow(new NotFoundException("Version not found"));

        mockMvc.perform(get(base + "/" + versionId).with(user(ownerId.toString())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Version not found"));
    }

    @Test
    void malformedVersionIdIs400() throws Exception {
        mockMvc.perform(get(base + "/not-a-uuid").with(user(ownerId.toString())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // ---------- POST /versions/{id}/restore ----------

    @Test
    void restoreReturnsRestoredDiagramAndBackup() throws Exception {
        UUID backupId = UUID.randomUUID();
        Diagram restored = new Diagram(diagramId, "Shop ERD", "ERD", DATA, 6, false, ownerId, ownerId, NOW, NOW);
        when(versionService.restore(diagramId, versionId, Caller.user(ownerId)))
                .thenReturn(new RestoreResult(restored, version(backupId, 8)));

        mockMvc.perform(post(base + "/" + versionId + "/restore").with(user(ownerId.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diagramId").value(diagramId.toString()))
                .andExpect(jsonPath("$.version").value(6))
                .andExpect(jsonPath("$.data.nodes[0].id").value("n1"))
                .andExpect(jsonPath("$.backupVersion.id").value(backupId.toString()))
                .andExpect(jsonPath("$.backupVersion.versionNumber").value(8));
    }

    @Test
    void restoreForbiddenIs403() throws Exception {
        when(versionService.restore(any(), any(), any())).thenThrow(new ForbiddenException("no edit"));

        mockMvc.perform(post(base + "/" + versionId + "/restore").with(user(ownerId.toString())))
                .andExpect(status().isForbidden());
    }

    @Test
    void restoreAfterConcurrentUpdateIs409() throws Exception {
        when(versionService.restore(any(), any(), any())).thenThrow(new ConflictException("stale"));

        mockMvc.perform(post(base + "/" + versionId + "/restore").with(user(ownerId.toString())))
                .andExpect(status().isConflict());
    }

    private DiagramVersion version(UUID id, int number) {
        return new DiagramVersion(id, diagramId, number, DATA, "note " + number, ownerId, NOW);
    }
}
