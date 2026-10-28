package com.diagramio.api.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.diagramio.config.SecurityConfig;
import com.diagramio.domain.access.Caller;
import com.diagramio.domain.access.ShareRole;
import com.diagramio.domain.auth.AuthService;
import com.diagramio.domain.auth.AuthTokens;
import com.diagramio.domain.exception.ConflictException;
import com.diagramio.domain.exception.UnauthorizedException;
import com.diagramio.domain.user.User;
import com.diagramio.domain.share.ShareService;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final User USER = new User(USER_ID, "minh", "minh@gmail.com", "$2a$10$hash",
            Instant.parse("2026-10-26T10:00:00Z"));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider; // SecurityConfig cần; mặc định coi mọi token là không hợp lệ

    @MockitoBean
    private ShareService shareService; // filter X-Share-Token cần; mặc định coi mọi token là không hợp lệ

    // ---- register ----

    @Test
    void registerReturns201WithoutPasswordHash() throws Exception {
        when(authService.register("minh", "minh@gmail.com", "secret123")).thenReturn(USER);

        mockMvc.perform(json(post("/api/v1/auth/register"),
                        "{\"username\":\"minh\",\"email\":\"minh@gmail.com\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.username").value("minh"))
                .andExpect(jsonPath("$.email").value("minh@gmail.com"))
                .andExpect(jsonPath("$.createdAt").value("2026-10-26T10:00:00Z"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void registerRejectsInvalidEmail() throws Exception {
        mockMvc.perform(json(post("/api/v1/auth/register"),
                        "{\"username\":\"minh\",\"email\":\"not-an-email\",\"password\":\"secret123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        verify(authService, never()).register(any(), any(), any());
    }

    @Test
    void registerRejectsShortPassword() throws Exception {
        mockMvc.perform(json(post("/api/v1/auth/register"),
                        "{\"username\":\"minh\",\"email\":\"minh@gmail.com\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
        verify(authService, never()).register(any(), any(), any());
    }

    @Test
    void registerRejectsMissingFields() throws Exception {
        mockMvc.perform(json(post("/api/v1/auth/register"), "{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerRejectsMalformedJson() throws Exception {
        mockMvc.perform(json(post("/api/v1/auth/register"), "{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerDuplicateReturns409() throws Exception {
        when(authService.register(any(), any(), any())).thenThrow(new ConflictException("Email is already registered"));

        mockMvc.perform(json(post("/api/v1/auth/register"),
                        "{\"username\":\"uet\",\"email\":\"uet@gmail.com\",\"password\":\"secret123\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email is already registered"));
    }

    // ---- login ----

    @Test
    void loginReturnsTokens() throws Exception {
        when(authService.login("uet@gmail.com", "uet123456")).thenReturn(new AuthTokens("access", "refresh"));

        mockMvc.perform(json(post("/api/v1/auth/login"), "{\"email\":\"uet@gmail.com\",\"password\":\"uet123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access"))
                .andExpect(jsonPath("$.refreshToken").value("refresh"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void loginWithWrongCredentialsReturns401() throws Exception {
        when(authService.login(any(), any())).thenThrow(new UnauthorizedException("Invalid email or password"));

        mockMvc.perform(json(post("/api/v1/auth/login"), "{\"email\":\"uet@gmail.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void loginRejectsBlankPassword() throws Exception {
        mockMvc.perform(json(post("/api/v1/auth/login"), "{\"email\":\"uet@gmail.com\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    // ---- refresh-token ----

    @Test
    void refreshTokenReturnsNewTokens() throws Exception {
        when(authService.refresh("old")).thenReturn(new AuthTokens("access2", "refresh2"));

        mockMvc.perform(json(post("/api/v1/auth/refresh-token"), "{\"refreshToken\":\"old\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access2"))
                .andExpect(jsonPath("$.refreshToken").value("refresh2"));
    }

    @Test
    void refreshTokenWithInvalidTokenReturns401() throws Exception {
        when(authService.refresh(any())).thenThrow(new UnauthorizedException("Invalid or expired refresh token"));

        mockMvc.perform(json(post("/api/v1/auth/refresh-token"), "{\"refreshToken\":\"bad\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshTokenRejectsBlankToken() throws Exception {
        mockMvc.perform(json(post("/api/v1/auth/refresh-token"), "{\"refreshToken\":\" \"}"))
                .andExpect(status().isBadRequest());
    }

    // ---- logout ----

    @Test
    void logoutRevokesTokenOfCurrentUser() throws Exception {
        mockMvc.perform(json(post("/api/v1/auth/logout"), "{\"refreshToken\":\"rt\"}").with(loggedIn()))
                .andExpect(status().isNoContent());

        verify(authService).logout(USER_ID, "rt");
    }

    @Test
    void logoutRequiresAuthentication() throws Exception {
        mockMvc.perform(json(post("/api/v1/auth/logout"), "{\"refreshToken\":\"rt\"}"))
                .andExpect(status().isUnauthorized());

        verify(authService, never()).logout(any(), any());
    }

    @Test
    void logoutWithOnlyShareLinkReturns401() throws Exception {
        mockMvc.perform(json(post("/api/v1/auth/logout"), "{\"refreshToken\":\"rt\"}").with(shareLinkOnly()))
                .andExpect(status().isUnauthorized());

        verify(authService, never()).logout(any(), any());
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    /** Giả lập người đã đăng nhập như filter JWT sẽ làm: principal là id người dùng. */
    private static RequestPostProcessor loggedIn() {
        return authentication(new UsernamePasswordAuthenticationToken(USER_ID, null, List.of()));
    }

    /** Chỉ cầm link chia sẻ (filter X-Share-Token đặt principal là Caller, không có userId). */
    private static RequestPostProcessor shareLinkOnly() {
        return authentication(new UsernamePasswordAuthenticationToken(
                Caller.shareToken(UUID.randomUUID(), ShareRole.EDIT), null, List.of()));
    }
}
