package com.diagramio.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.diagramio.config.SecurityConfig;
import com.diagramio.domain.DatabaseHealth;
import com.diagramio.domain.share.ShareService;
import com.diagramio.infrastructure.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HealthController.class)
@Import(SecurityConfig.class)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider; // SecurityConfig cần; mặc định coi mọi token là không hợp lệ

    @MockitoBean
    private ShareService shareService; // filter X-Share-Token cần; mặc định coi mọi token là không hợp lệ

    @MockitoBean
    private DatabaseHealth databaseHealth;

    @BeforeEach
    void databaseIsUp() {
        when(databaseHealth.isUp()).thenReturn(true);
    }

    @Test
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.database").value("UP"));
    }

    @Test
    void healthIs503WhenDatabaseIsDown() throws Exception {
        when(databaseHealth.isUp()).thenReturn(false);

        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.database").value("DOWN"));
    }

    @Test
    void publicEndpointStillWorksWithInvalidToken() throws Exception {
        mockMvc.perform(get("/api/v1/health").header("Authorization", "Bearer invalid"))
                .andExpect(status().isOk());
    }

    @Test
    void openApiDocsArePublic() throws Exception {
        // springdoc không được nạp đầy đủ trong WebMvcTest; chỉ cần chắc security không chặn (401/403)
        for (String path : new String[] {"/v3/api-docs", "/v3/api-docs.yaml"}) {
            mockMvc.perform(get(path))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotIn(401, 403));
        }
    }

    @Test
    void otherEndpointsRequireAuthenticationWithJsonError() throws Exception {
        mockMvc.perform(get("/api/v1/diagrams"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Authentication required"))
                .andExpect(jsonPath("$.path").value("/api/v1/diagrams"));
    }
}
