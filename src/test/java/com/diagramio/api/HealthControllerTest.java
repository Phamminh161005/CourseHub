package com.diagramio.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.diagramio.config.SecurityConfig;
import com.diagramio.domain.share.ShareService;
import com.diagramio.infrastructure.security.JwtTokenProvider;
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

    @Test
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void publicEndpointStillWorksWithInvalidToken() throws Exception {
        mockMvc.perform(get("/api/v1/health").header("Authorization", "Bearer invalid"))
                .andExpect(status().isOk());
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
