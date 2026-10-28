package com.diagramio.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

class UserE2eTest extends E2eTestBase {

    // ------------------------------------------------------------------ GET /users/me

    @Test
    void meAuthenticatedReturnsOwnProfileWithoutSecrets() {
        Actor a = registerAndLogin();
        Res r = get(API + "/users/me").bearer(a.accessToken()).send();
        assertStatus(r, 200);
        JsonNode j = r.json();
        assertThat(j.path("id").asText()).isEqualTo(a.userId());
        assertThat(j.path("email").asText()).isEqualTo(a.email());
        assertThat(j.path("username").asText()).isEqualTo(a.username());
        assertThat(j.path("createdAt").isTextual()).isTrue();
        assertThat(j.has("password")).isFalse();
        assertThat(j.has("passwordHash")).isFalse();
        assertThat(r.body()).doesNotContain("$2a$").doesNotContain("$2b$");
    }

    @Test
    void meAnonymousReturns401() {
        assertError(get(API + "/users/me").send(), 401);
    }

    @Test
    void meInvalidBearerReturns401() {
        assertError(get(API + "/users/me").bearer("abc.def.ghi").send(), 401);
    }

    @Test
    void meTamperedJwtReturns401() {
        Actor a = registerAndLogin();
        String[] parts = a.accessToken().split("\\.");
        String tampered = parts[0] + "." + parts[1] + "." + new StringBuilder(parts[2]).reverse();
        assertError(get(API + "/users/me").bearer(tampered).send(), 401);
    }

    @Test
    void meWithOnlyShareTokenReturns401() {
        Actor owner = registerAndLogin();
        String token = shareToken(owner, createDiagramId(owner), "edit");
        // A share token is not a user identity.
        Res r = get(API + "/users/me").share(token).send();
        assertError(r, 401);
    }

    @Test
    void changePasswordWithOnlyShareTokenReturns401() {
        Actor owner = registerAndLogin();
        String token = shareToken(owner, createDiagramId(owner), "edit");
        Res r = patch(API + "/users/me/password").share(token)
                .json(obj("currentPassword", PASSWORD, "newPassword", "NewPassw0rd!9")).send();
        assertError(r, 401);
    }

    @Test
    void logoutWithOnlyShareTokenReturns401() {
        Actor owner = registerAndLogin();
        String token = shareToken(owner, createDiagramId(owner), "edit");
        Res r = post(API + "/auth/logout").share(token).json(obj("refreshToken", owner.refreshToken())).send();
        Res refresh = post(API + "/auth/refresh-token").json(obj("refreshToken", owner.refreshToken())).send();
        assertThat(refresh.status()).as("logout status was %s; owner's refresh token must survive a link-only logout: %s",
                r.status(), refresh.body()).isEqualTo(200);
        assertError(r, 401);
    }

    @Test
    void listMyDiagramsWithOnlyShareTokenReturns401() {
        Actor owner = registerAndLogin();
        String token = shareToken(owner, createDiagramId(owner), "edit");
        Res r = get(API + "/diagrams").share(token).send();
        assertError(r, 401);
    }

    // ------------------------------------------------------------------ PATCH /users/me/password

    @Test
    void changePasswordCorrectOldPasswordReturns204AndNewPasswordWorks() {
        Actor a = registerAndLogin();
        String newPw = "NewPassw0rd!9";
        Res r = patch(API + "/users/me/password").bearer(a.accessToken())
                .json(obj("currentPassword", PASSWORD, "newPassword", newPw)).send();
        assertStatus(r, 204);
        assertError(login(a.email(), PASSWORD), 401);
        assertStatus(login(a.email(), newPw), 200);
    }

    @Test
    void changePasswordWrongOldPasswordReturns400AndKeepsOldPassword() {
        Actor a = registerAndLogin();
        Res r = patch(API + "/users/me/password").bearer(a.accessToken())
                .json(obj("currentPassword", "WrongOld123", "newPassword", "NewPassw0rd!9")).send();
        // README error table: "400 ... mật khẩu cũ sai"
        assertError(r, 400);
        assertStatus(login(a.email(), PASSWORD), 200);
        assertError(login(a.email(), "NewPassw0rd!9"), 401);
    }

    @Test
    void changePasswordMissingOldPasswordReturns400() {
        Actor a = registerAndLogin();
        Res r = patch(API + "/users/me/password").bearer(a.accessToken())
                .json(obj("newPassword", "NewPassw0rd!9")).send();
        assertError(r, 400);
        assertStatus(login(a.email(), PASSWORD), 200);
    }

    @Test
    void changePasswordTooShortNewPasswordReturns400() {
        Actor a = registerAndLogin();
        Res r = patch(API + "/users/me/password").bearer(a.accessToken())
                .json(obj("currentPassword", PASSWORD, "newPassword", "short")).send();
        assertError(r, 400);
        assertStatus(login(a.email(), PASSWORD), 200);
    }

    @Test
    void changePasswordAnonymousReturns401() {
        Res r = patch(API + "/users/me/password")
                .json(obj("currentPassword", PASSWORD, "newPassword", "NewPassw0rd!9")).send();
        assertError(r, 401);
    }

    @Test
    void changePasswordMalformedJsonReturns400() {
        Actor a = registerAndLogin();
        assertError(patch(API + "/users/me/password").bearer(a.accessToken()).rawJson("{oops").send(), 400);
    }
}
