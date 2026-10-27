package com.diagramio.api;

import com.diagramio.config.SecurityConfig;
import com.diagramio.domain.model.DiagramShare;
import com.diagramio.domain.model.SharedDiagramView;
import com.diagramio.domain.service.ShareService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {DiagramShareController.class, PublicShareController.class})
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class DiagramShareControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShareService shareService;

    private final UUID diagramId = UUID.randomUUID();
    private final UUID shareId = UUID.randomUUID();

    @Test
    @DisplayName("GET /api/v1/shares/{token} là công khai (Public), không cần đăng nhập")
    void publicShare_Success() throws Exception {
        SharedDiagramView view = new SharedDiagramView(
                diagramId,
                "Sơ đồ ERD bán hàng",
                "ERD",
                "{\"nodes\":[], \"edges\":[]}",
                "view",
                1,
                false,
                Instant.now(),
                Instant.now()
        );
        when(shareService.getDiagramByShareToken("sample_token")).thenReturn(view);

        mockMvc.perform(get("/api/v1/shares/sample_token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(diagramId.toString()))
                .andExpect(jsonPath("$.title").value("Sơ đồ ERD bán hàng"))
                .andExpect(jsonPath("$.role").value("view"));
    }

    @Test
    @DisplayName("GET /api/v1/diagrams/{id}/shares yêu cầu đăng nhập")
    void getShares_Unauthenticated_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/diagrams/" + diagramId + "/shares"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    @DisplayName("GET /api/v1/diagrams/{id}/shares khi đã đăng nhập trả về 200 OK")
    void getShares_Authenticated_ReturnsOk() throws Exception {
        DiagramShare share = new DiagramShare(shareId, diagramId, "token123", "view", Instant.now());
        when(shareService.getShares(eq(diagramId), any())).thenReturn(List.of(share));

        mockMvc.perform(get("/api/v1/diagrams/" + diagramId + "/shares"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].shareToken").value("token123"))
                .andExpect(jsonPath("$[0].role").value("view"));
    }

    @Test
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    @DisplayName("POST /api/v1/diagrams/{id}/shares tạo link chia sẻ trả về 201 Created")
    void createShare_Success() throws Exception {
        DiagramShare created = new DiagramShare(shareId, diagramId, "token_new", "edit", Instant.now());
        when(shareService.createShare(eq(diagramId), eq("edit"), any())).thenReturn(created);

        mockMvc.perform(post("/api/v1/diagrams/" + diagramId + "/shares")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"edit\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shareToken").value("token_new"))
                .andExpect(jsonPath("$.role").value("edit"));
    }

    @Test
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    @DisplayName("POST /api/v1/diagrams/{id}/shares với role không hợp lệ trả về 400 Bad Request")
    void createShare_InvalidRole_ReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/diagrams/" + diagramId + "/shares")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"admin\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    @DisplayName("PATCH /api/v1/diagrams/{id}/shares/{shareId} đổi role trả về 200 OK")
    void updateShare_Success() throws Exception {
        DiagramShare updated = new DiagramShare(shareId, diagramId, "token123", "edit", Instant.now());
        when(shareService.updateShareRole(eq(diagramId), eq(shareId), eq("edit"), any())).thenReturn(updated);

        mockMvc.perform(patch("/api/v1/diagrams/" + diagramId + "/shares/" + shareId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"edit\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("edit"));
    }

    @Test
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    @DisplayName("DELETE /api/v1/diagrams/{id}/shares/{shareId} thu hồi share trả về 204 No Content")
    void revokeShare_Success() throws Exception {
        doNothing().when(shareService).revokeShare(eq(diagramId), eq(shareId), any());

        mockMvc.perform(delete("/api/v1/diagrams/" + diagramId + "/shares/" + shareId))
                .andExpect(status().isNoContent());
    }
}
