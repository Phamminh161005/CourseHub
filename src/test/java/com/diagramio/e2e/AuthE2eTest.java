package com.diagramio.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class AuthE2eTest extends E2eTestBase {

    // ------------------------------------------------------------------ register

    @Test
    void registerValidInputReturns201WithUserWithoutPassword() {
        String u = "u" + uniq();
        String email = u + "@e2e.test";
        Res r = register(u, email, PASSWORD);
        assertStatus(r, 201);
        JsonNode j = r.json();
        assertUuid(j.path("id"));
        assertThat(j.path("username").asText()).isEqualTo(u);
        assertThat(j.path("email").asText()).isEqualTo(email);
        assertThat(j.path("createdAt").isTextual()).isTrue();
        assertThat(j.has("password")).isFalse();
        assertThat(j.has("passwordHash")).isFalse();
        assertThat(r.body()).doesNotContain(PASSWORD).doesNotContain("$2a$").doesNotContain("$2b$");
    }

    @Test
    void registerDuplicateEmailReturns409() {
        Actor a = registerAndLogin();
        Res r = register("u" + uniq(), a.email(), PASSWORD);
        assertError(r, 409);
    }

    @Test
    void registerDuplicateUsernameReturns409() {
        Actor a = registerAndLogin();
        Res r = register(a.username(), "x" + uniq() + "@e2e.test", PASSWORD);
        assertError(r, 409);
    }

    @Test
    void registerConcurrentSameEmailOnlyOneSucceedsOthersGet409() throws Exception {
        String email = "race" + uniq() + "@e2e.test";
        List<Res> results = concurrently(8, () -> register("r" + uniq(), email, PASSWORD));
        assertOneCreatedRestConflict(results);
        assertStatus(login(email, PASSWORD), 200);
    }

    @Test
    void registerConcurrentSameUsernameOnlyOneSucceedsOthersGet409() throws Exception {
        String username = "race" + uniq();
        List<Res> results = concurrently(8, () -> register(username, "r" + uniq() + "@e2e.test", PASSWORD));
        assertOneCreatedRestConflict(results);
    }

    private static void assertOneCreatedRestConflict(List<Res> results) {
        int created = 0;
        for (Res r : results) {
            if (r.status() == 201) {
                created++;
            } else {
                assertError(r, 409);
            }
        }
        assertThat(created).isEqualTo(1);
    }

    private static List<Res> concurrently(int n, Callable<Res> call) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Res>> fs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            fs.add(pool.submit(() -> {
                start.await();
                return call.call();
            }));
        }
        start.countDown();
        List<Res> out = new ArrayList<>();
        for (Future<Res> f : fs) {
            out.add(f.get());
        }
        pool.shutdown();
        return out;
    }

    @Test
    void registerMissingEmailReturns400() {
        Res r = post(API + "/auth/register").json(obj("username", "u" + uniq(), "password", PASSWORD)).send();
        assertError(r, 400);
    }

    @Test
    void registerMissingPasswordReturns400() {
        String u = "u" + uniq();
        Res r = post(API + "/auth/register").json(obj("username", u, "email", u + "@e2e.test")).send();
        assertError(r, 400);
    }

    @Test
    void registerMissingUsernameReturns400() {
        // SPEC-AMBIGUOUS: openapi lists no required fields; username is a NOT NULL unique column, so we expect 400.
        Res r = post(API + "/auth/register").json(obj("email", "m" + uniq() + "@e2e.test", "password", PASSWORD)).send();
        assertError(r, 400);
    }

    @Test
    void registerInvalidEmailFormatReturns400() {
        Res r = register("u" + uniq(), "not-an-email", PASSWORD);
        assertError(r, 400);
    }

    @Test
    void registerShortPasswordReturns400() {
        String u = "u" + uniq();
        Res r = register(u, u + "@e2e.test", "short1");
        assertError(r, 400);
    }

    @Test
    void registerTooLongPasswordReturns400() {
        String u = "u" + uniq();
        Res r = register(u, u + "@e2e.test", "a".repeat(73));
        assertError(r, 400);
    }

    @Test
    void registerTooLongUsernameReturns400() {
        Res r = register("u".repeat(51), "l" + uniq() + "@e2e.test", PASSWORD);
        assertError(r, 400);
    }

    @Test
    void registerMalformedJsonReturns400() {
        Res r = post(API + "/auth/register").rawJson("{\"email\": ").send();
        assertError(r, 400);
    }

    // ------------------------------------------------------------------ login

    @Test
    void loginValidCredentialsReturnsAccessAndRefreshTokens() {
        String u = "u" + uniq();
        String email = u + "@e2e.test";
        assertStatus(register(u, email, PASSWORD), 201);
        Res r = login(email, PASSWORD);
        assertStatus(r, 200);
        JsonNode j = r.json();
        assertThat(j.path("accessToken").asText()).isNotBlank();
        assertThat(j.path("refreshToken").asText()).isNotBlank();
        assertThat(j.path("accessToken").asText()).isNotEqualTo(j.path("refreshToken").asText());
        assertThat(j.path("tokenType").asText()).isEqualToIgnoringCase("Bearer");
        // the access token is a JWT
        assertThat(j.path("accessToken").asText().split("\\.")).hasSize(3);
    }

    @Test
    void loginWrongPasswordReturns401() {
        Actor a = registerAndLogin();
        assertError(login(a.email(), "WrongPassword1"), 401);
    }

    @Test
    void loginUnknownEmailReturns401WithSameMessageAsWrongPassword() {
        Actor a = registerAndLogin();
        Res wrongPw = login(a.email(), "WrongPassword1");
        Res unknown = login("nobody" + uniq() + "@e2e.test", "WrongPassword1");
        assertError(unknown, 401);
        assertError(wrongPw, 401);
        // README: "Sai email hay sai mật khẩu đều trả cùng một thông báo"
        assertThat(unknown.json().path("message").asText()).isEqualTo(wrongPw.json().path("message").asText());
    }

    @Test
    void loginMissingPasswordReturns400() {
        Res r = post(API + "/auth/login").json(obj("email", "a@b.c")).send();
        assertError(r, 400);
    }

    @Test
    void loginBlankEmailReturns400() {
        Res r = post(API + "/auth/login").json(obj("email", "", "password", PASSWORD)).send();
        assertError(r, 400);
    }

    @Test
    void loginTwiceIssuesDifferentRefreshTokens() {
        Actor a = registerAndLogin();
        Res second = login(a.email(), PASSWORD);
        assertStatus(second, 200);
        assertThat(second.json().path("refreshToken").asText()).isNotEqualTo(a.refreshToken());
    }

    // ------------------------------------------------------------------ refresh

    @Test
    void refreshValidTokenReturnsNewTokenPairThatWorks() {
        Actor a = registerAndLogin();
        Res r = post(API + "/auth/refresh-token").json(obj("refreshToken", a.refreshToken())).send();
        assertStatus(r, 200);
        JsonNode j = r.json();
        String newAccess = j.path("accessToken").asText();
        String newRefresh = j.path("refreshToken").asText();
        assertThat(newAccess).isNotBlank();
        assertThat(newRefresh).isNotBlank().isNotEqualTo(a.refreshToken());
        assertStatus(get(API + "/users/me").bearer(newAccess).send(), 200);
    }

    @Test
    void refreshRotatedOldTokenReturns401() {
        Actor a = registerAndLogin();
        Res first = post(API + "/auth/refresh-token").json(obj("refreshToken", a.refreshToken())).send();
        assertStatus(first, 200);
        Res reuse = post(API + "/auth/refresh-token").json(obj("refreshToken", a.refreshToken())).send();
        assertError(reuse, 401);
        // the newly issued refresh token still works
        Res next = post(API + "/auth/refresh-token")
                .json(obj("refreshToken", first.json().path("refreshToken").asText())).send();
        assertStatus(next, 200);
    }

    @Test
    void refreshUnknownTokenReturns401() {
        Res r = post(API + "/auth/refresh-token").json(obj("refreshToken", "garbage-" + uniq())).send();
        assertError(r, 401);
    }

    @Test
    void refreshWithAccessTokenInsteadOfRefreshTokenReturns401() {
        Actor a = registerAndLogin();
        Res r = post(API + "/auth/refresh-token").json(obj("refreshToken", a.accessToken())).send();
        assertError(r, 401);
    }

    @Test
    void refreshMissingTokenReturns400() {
        Res r = post(API + "/auth/refresh-token").json(obj()).send();
        assertError(r, 400);
    }

    @Test
    void refreshBlankTokenReturns400() {
        Res r = post(API + "/auth/refresh-token").json(obj("refreshToken", "")).send();
        assertError(r, 400);
    }

    @Test
    void refreshTokenUsedAsBearerReturns401() {
        Actor a = registerAndLogin();
        assertError(get(API + "/users/me").bearer(a.refreshToken()).send(), 401);
    }

    // ------------------------------------------------------------------ logout

    @Test
    void logoutAuthenticatedReturns204AndRevokesRefreshToken() {
        Actor a = registerAndLogin();
        Res r = post(API + "/auth/logout").bearer(a.accessToken()).json(obj("refreshToken", a.refreshToken())).send();
        assertStatus(r, 204);
        Res refresh = post(API + "/auth/refresh-token").json(obj("refreshToken", a.refreshToken())).send();
        assertError(refresh, 401);
    }

    @Test
    void logoutAnonymousReturns401AndDoesNotRevoke() {
        Actor a = registerAndLogin();
        Res r = post(API + "/auth/logout").json(obj("refreshToken", a.refreshToken())).send();
        assertError(r, 401);
        Res refresh = post(API + "/auth/refresh-token").json(obj("refreshToken", a.refreshToken())).send();
        assertStatus(refresh, 200);
    }

    @Test
    void logoutWithAnotherUsersRefreshTokenDoesNotRevokeIt() {
        // Security: user B must not be able to log user A out by posting A's refresh token.
        // SPEC-AMBIGUOUS: response code unspecified (204 no-op, 403, 404 all reasonable); we only require A's token survives.
        Actor a = registerAndLogin();
        Actor b = registerAndLogin();
        Res r = post(API + "/auth/logout").bearer(b.accessToken()).json(obj("refreshToken", a.refreshToken())).send();
        assertThat(r.status()).isIn(204, 400, 401, 403, 404);
        Res refresh = post(API + "/auth/refresh-token").json(obj("refreshToken", a.refreshToken())).send();
        assertThat(refresh.status()).as("A's refresh token must still be valid: %s", refresh.body()).isEqualTo(200);
    }

    @Test
    void logoutMissingRefreshTokenReturns400() {
        Actor a = registerAndLogin();
        Res r = post(API + "/auth/logout").bearer(a.accessToken()).json(obj()).send();
        assertError(r, 400);
    }

    @Test
    void logoutOnlyRevokesGivenSessionNotOtherSessions() {
        Actor a = registerAndLogin();
        Res second = login(a.email(), PASSWORD);
        String otherRefresh = second.json().path("refreshToken").asText();
        assertStatus(post(API + "/auth/logout").bearer(a.accessToken()).json(obj("refreshToken", a.refreshToken())).send(), 204);
        // "revoke the current refresh token" - other sessions are untouched
        assertStatus(post(API + "/auth/refresh-token").json(obj("refreshToken", otherRefresh)).send(), 200);
    }
}
