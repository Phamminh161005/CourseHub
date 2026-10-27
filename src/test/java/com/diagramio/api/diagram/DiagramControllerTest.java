package com.diagramio.api.diagram;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.diagramio.api.GlobalExceptionHandler;
import com.diagramio.config.SecurityConfig;
import com.diagramio.domain.diagram.Diagram;
import com.diagramio.domain.diagram.DiagramService;
import com.diagramio.domain.exception.DiagramNotFoundException;
import com.diagramio.domain.exception.OptimisticLockConflictException;
import com.diagramio.domain.exception.UnauthorizedDiagramAccessException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DiagramController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class DiagramControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID DIAGRAM_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DiagramService diagramService;

    @Test
    void createReturns201ForValidRequest() throws Exception {
        when(diagramService.createDiagram(any(Diagram.class))).thenReturn(diagram());

        mockMvc.perform(post("/api/v1/diagrams")
                        .with(user(USER_ID.toString()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Orders","diagramType":"ERD","isPublic":false}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/diagrams/" + DIAGRAM_ID));
    }

    @Test
    void createReturns400ForInvalidDiagramType() throws Exception {
        mockMvc.perform(post("/api/v1/diagrams")
                        .with(user(USER_ID.toString()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Orders","diagramType":"BPMN","isPublic":false}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateReturns200ForValidRequest() throws Exception {
        when(diagramService.updateDiagram(eq(DIAGRAM_ID), eq("Orders"), eq("ERD"),
                eq(DiagramService.DEFAULT_DATA), eq(false), eq(USER_ID), eq(1)))
                .thenReturn(diagram());

        mockMvc.perform(put("/api/v1/diagrams/{id}", DIAGRAM_ID)
                        .with(user(USER_ID.toString()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Orders","diagramType":"ERD","version":1,
                                 "data":"{\\"nodes\\": [], \\"edges\\": []}","isPublic":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(DIAGRAM_ID.toString()));
    }

    @Test
    void updateReturns409ForVersionConflict() throws Exception {
        when(diagramService.updateDiagram(eq(DIAGRAM_ID), eq("Orders"), eq("ERD"),
                eq(DiagramService.DEFAULT_DATA), eq(false), eq(USER_ID), eq(1)))
                .thenThrow(new OptimisticLockConflictException(DIAGRAM_ID, 1, 2));

        mockMvc.perform(put("/api/v1/diagrams/{id}", DIAGRAM_ID)
                        .with(user(USER_ID.toString()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Orders","diagramType":"ERD","version":1,
                                 "data":"{\\"nodes\\": [], \\"edges\\": []}","isPublic":false}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Dữ liệu sơ đồ đã bị thay đổi bởi phiên làm việc khác. Vui lòng làm mới trang!"));
    }

    @Test
    void deleteReturns403ForNonOwner() throws Exception {
        doThrow(new UnauthorizedDiagramAccessException("Only the diagram owner can delete it"))
                .when(diagramService).deleteDiagram(DIAGRAM_ID, USER_ID);

        mockMvc.perform(delete("/api/v1/diagrams/{id}", DIAGRAM_ID)
                        .with(user(USER_ID.toString())))
                .andExpect(status().isForbidden());
    }

    @Test
    void getReturns404WhenDiagramDoesNotExist() throws Exception {
        when(diagramService.getDiagramById(DIAGRAM_ID, null, false))
                .thenThrow(new DiagramNotFoundException("Diagram not found: " + DIAGRAM_ID));

        mockMvc.perform(get("/api/v1/diagrams/{id}", DIAGRAM_ID))
                .andExpect(status().isNotFound());
    }

    private Diagram diagram() {
        return new Diagram(DIAGRAM_ID, "Orders", "ERD", DiagramService.DEFAULT_DATA, 1,
                false, USER_ID, USER_ID, null, null);
    }
}
