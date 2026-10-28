package com.diagramio.api.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.diagramio.config.SecurityConfig;
import com.diagramio.domain.access.Caller;
import com.diagramio.domain.access.ShareRole;
import com.diagramio.domain.exception.InvalidInputException;
import com.diagramio.domain.user.User;
import com.diagramio.domain.user.UserService;
import com.diagramio.domain.share.ShareService;
import com.diagramio.infrastructure.security.JwtTokenProvider;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final User USER = new User(USER_ID, "uet", "uet@gmail.com", "$2a$10$hash",
            Instant.parse("2026-01-01T00:00:00Z"));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider; // SecurityConfig cần; mặc định coi mọi token là không hợp lệ

    @MockitoBean
    private ShareService shareService; // filter X-Share-Token cần; mặc định coi mọi token là không hợp lệ

    // ---- qua filter JWT thật (header Authorization) ----

    @Test
    void meWithValidBearerTokenGoesThroughJwtFilter() throws Exception {
        when(jwtTokenProvider.parseUserId("valid-token")).thenReturn(Optional.of(USER_ID));
        when(userService.getById(USER_ID)).thenReturn(USER);

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID.toString()));
    }

    @Test
    void meWithInvalidBearerTokenReturns401Json() throws Exception {
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer expired-or-forged"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Authentication required"));

        verify(userService, never()).getById(any());
    }

    @Test
    void meWithoutBearerPrefixReturns401() throws Exception {
        when(jwtTokenProvider.parseUserId("valid-token")).thenReturn(Optional.of(USER_ID));

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "valid-token"))
                .andExpect(status().isUnauthorized());
    }

    // ---- giả lập người đã đăng nhập ----

    @Test
    void meReturnsCurrentUserWithoutPasswordHash() throws Exception {
        when(userService.getById(USER_ID)).thenReturn(USER);

        mockMvc.perform(get("/api/v1/users/me").with(loggedIn()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.username").value("uet"))
                .andExpect(jsonPath("$.email").value("uet@gmail.com"))
                .andExpect(jsonPath("$.createdAt").value("2026-01-01T00:00:00Z"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void meRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePasswordReturns204() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me/password").with(loggedIn())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"uet123456\",\"newPassword\":\"newpass123\"}"))
                .andExpect(status().isNoContent());

        verify(userService).changePassword(USER_ID, "uet123456", "newpass123");
    }

    @Test
    void changePasswordWithWrongCurrentPasswordReturns400() throws Exception {
        doThrow(new InvalidInputException("Current password is incorrect"))
                .when(userService).changePassword(any(), any(), any());

        mockMvc.perform(patch("/api/v1/users/me/password").with(loggedIn())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong\",\"newPassword\":\"newpass123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Current password is incorrect"));
    }

    @Test
    void changePasswordRejectsShortNewPassword() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me/password").with(loggedIn())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"uet123456\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest());

        verify(userService, never()).changePassword(any(), any(), any());
    }

    @Test
    void meWithOnlyShareLinkReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me").with(shareLinkOnly()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void changePasswordWithOnlyShareLinkReturns401() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me/password").with(shareLinkOnly())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"uet123456\",\"newPassword\":\"newpass123\"}"))
                .andExpect(status().isUnauthorized());
        verify(userService, never()).changePassword(any(), any(), any());
    }

    @Test
    void changePasswordRequiresAuthentication() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"uet123456\",\"newPassword\":\"newpass123\"}"))
                .andExpect(status().isUnauthorized());

        verify(userService, never()).changePassword(any(), any(), any());
    }

    private static RequestPostProcessor loggedIn() {
        return authentication(new UsernamePasswordAuthenticationToken(USER_ID, null, List.of()));
    }

    /** Chỉ cầm link chia sẻ (filter X-Share-Token đặt principal là Caller, không có userId). */
    private static RequestPostProcessor shareLinkOnly() {
        return authentication(new UsernamePasswordAuthenticationToken(
                Caller.shareToken(UUID.randomUUID(), ShareRole.EDIT), null, List.of()));
    }
}
