package com.diagramio.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class ShareE2eTest extends E2eTestBase {

    private String shares(String diagramId) {
        return API + "/diagrams/" + diagramId + "/shares";
    }

    private static void assertShareShape(JsonNode s) {
        assertUuid(s.path("id"));
        assertUuid(s.path("diagramId"));
        assertThat(s.path("shareToken").asText()).isNotBlank();
        assertThat(s.path("shareUrl").asText()).isNotBlank();
        assertThat(s.path("role").asText()).isIn("view", "edit");
        assertThat(s.path("createdAt").isTextual()).isTrue();
    }

    // ------------------------------------------------------------------ POST shares

    @Test
    void createShareOwnerReturns201WithTokenAndUrl() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        Res r = post(shares(id)).bearer(a.accessToken()).json(obj("role", "view")).send();
        assertStatus(r, 201);
        JsonNode s = r.json();
        assertShareShape(s);
        assertThat(s.path("diagramId").asText()).isEqualTo(id);
        assertThat(s.path("role").asText()).isEqualTo("view");
        String token = s.path("shareToken").asText();
        // cryptographically random: at least 128 bits of entropy when hex/base64 encoded
        assertThat(token.length()).isGreaterThanOrEqualTo(32);
        assertThat(token).doesNotContain(id).doesNotContain(id.replace("-", ""));
        String url = s.path("shareUrl").asText();
        assertThat(url).contains(token).contains(API + "/shares/");
    }

    @Test
    void createShareUrlIsDirectlyUsable() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        JsonNode s = createShare(a, id, "edit");
        Res r = get(s.path("shareUrl").asText()).send();
        assertStatus(r, 200);
        assertThat(r.json().path("id").asText()).isEqualTo(id);
    }

    @Test
    void createShareEditRoleReturns201() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        assertThat(createShare(a, id, "edit").path("role").asText()).isEqualTo("edit");
    }

    @Test
    void createShareTokensAreUnique() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            tokens.add(shareToken(a, id, i % 2 == 0 ? "view" : "edit"));
        }
        assertThat(tokens).hasSize(10);
    }

    @Test
    void createShareDoesNotMakeDiagramPublic() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        createShare(a, id, "view");
        assertThat(getDiagram(a, id).path("isPublic").asBoolean()).isFalse();
        assertStatusIn(get(API + "/diagrams/" + id).send(), 401, 403);
    }

    @Test
    void createShareInvalidRoleReturns400() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        assertError(post(shares(id)).bearer(a.accessToken()).json(obj("role", "admin")).send(), 400);
    }

    @Test
    void createShareOwnerRoleReturns400() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        assertError(post(shares(id)).bearer(a.accessToken()).json(obj("role", "owner")).send(), 400);
    }

    @Test
    void createShareMissingRoleReturns400() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        assertError(post(shares(id)).bearer(a.accessToken()).json(obj()).send(), 400);
    }

    @Test
    void createShareMalformedJsonReturns400() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        assertError(post(shares(id)).bearer(a.accessToken()).rawJson("{role:").send(), 400);
    }

    @Test
    void createShareOtherUserReturns403() {
        Actor a = registerAndLogin();
        Actor b = registerAndLogin();
        String id = createDiagramId(a);
        assertError(post(shares(id)).bearer(b.accessToken()).json(obj("role", "edit")).send(), 403);
    }

    @Test
    void createShareOtherUserOnPublicDiagramReturns403() {
        Actor a = registerAndLogin();
        Actor b = registerAndLogin();
        String id = createDiagramId(a);
        setPublic(a, id, true);
        assertError(post(shares(id)).bearer(b.accessToken()).json(obj("role", "edit")).send(), 403);
    }

    @Test
    void createShareEditShareTokenIsDenied() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        String token = shareToken(a, id, "edit");
        Res r = post(shares(id)).share(token).json(obj("role", "edit")).send();
        // flow doc §7: "Người cầm link edit mà gọi các endpoint /shares sẽ nhận 401 hoặc 403"
        assertStatusIn(r, 401, 403);
        assertErrorBody(r);
    }

    @Test
    void createShareLoggedInEditorWithShareTokenReturns403() {
        Actor a = registerAndLogin();
        Actor b = registerAndLogin();
        String id = createDiagramId(a);
        String token = shareToken(a, id, "edit");
        assertError(post(shares(id)).as(b.withShare(token)).json(obj("role", "edit")).send(), 403);
    }

    @Test
    void createShareAnonymousReturns401() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        assertError(post(shares(id)).json(obj("role", "view")).send(), 401);
    }

    @Test
    void createShareUnknownDiagramReturns404() {
        Actor a = registerAndLogin();
        assertError(post(shares(UUID.randomUUID().toString())).bearer(a.accessToken()).json(obj("role", "view")).send(), 404);
    }

    // ------------------------------------------------------------------ GET shares

    @Test
    void listSharesOwnerReturnsAllShares() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        String t1 = shareToken(a, id, "view");
        String t2 = shareToken(a, id, "edit");
        createShare(a, createDiagramId(a), "view"); // other diagram, must not appear
        Res r = get(shares(id)).bearer(a.accessToken()).send();
        assertStatus(r, 200);
        // SPEC-AMBIGUOUS: openapi gives no 200 schema for this endpoint; we accept a bare array or a page.
        List<JsonNode> list = items(r.json());
        assertThat(list).hasSize(2);
        list.forEach(ShareE2eTest::assertShareShape);
        assertThat(list).extracting(n -> n.path("shareToken").asText()).containsExactlyInAnyOrder(t1, t2);
    }

    @Test
    void listSharesOtherUserReturns403AndLeaksNoTokens() {
        Actor a = registerAndLogin();
        Actor b = registerAndLogin();
        String id = createDiagramId(a);
        String token = shareToken(a, id, "edit");
        Res r = get(shares(id)).bearer(b.accessToken()).send();
        assertError(r, 403);
        assertThat(r.body()).doesNotContain(token);
    }

    @Test
    void listSharesViewShareTokenIsDeniedAndLeaksNoTokens() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        String editToken = shareToken(a, id, "edit");
        String viewToken = shareToken(a, id, "view");
        Res r = get(shares(id)).share(viewToken).send();
        assertStatusIn(r, 401, 403);
        assertThat(r.body()).doesNotContain(editToken);
    }

    @Test
    void listSharesEditShareTokenIsDenied() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        String editToken = shareToken(a, id, "edit");
        assertStatusIn(get(shares(id)).share(editToken).send(), 401, 403);
    }

    @Test
    void listSharesAnonymousReturns401() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        assertError(get(shares(id)).send(), 401);
    }

    @Test
    void listSharesUnknownDiagramReturns404() {
        Actor a = registerAndLogin();
        assertError(get(shares(UUID.randomUUID().toString())).bearer(a.accessToken()).send(), 404);
    }

    // ------------------------------------------------------------------ PATCH share

    @Test
    void updateShareOwnerViewToEditReturns200AndTakesEffectImmediately() {
        Actor a = registerAndLogin();
        JsonNode d = createDiagram(a, "UML");
        String id = d.path("id").asText();
        JsonNode s = createShare(a, id, "view");
        String token = s.path("shareToken").asText();
        // view link cannot edit
        assertError(updateDiagram(Actor.shareOnly(token), id, "T", "UML", d.path("version").asInt(), dataWith("X"), null), 403);

        Res r = patch(shares(id) + "/" + s.path("id").asText()).bearer(a.accessToken()).json(obj("role", "edit")).send();
        assertStatus(r, 200);
        assertShareShape(r.json());
        assertThat(r.json().path("role").asText()).isEqualTo("edit");
        assertThat(r.json().path("shareToken").asText()).isEqualTo(token);

        // same token now edits
        assertStatus(updateDiagram(Actor.shareOnly(token), id, "T", "UML", d.path("version").asInt(), dataWith("X"), null), 200);
        assertThat(get(API + "/shares/" + token).send().json().path("role").asText()).isEqualTo("edit");
    }

    @Test
    void updateShareOwnerEditToViewRemovesEditRightImmediately() {
        Actor a = registerAndLogin();
        JsonNode d = createDiagram(a, "UML");
        String id = d.path("id").asText();
        JsonNode s = createShare(a, id, "edit");
        String token = s.path("shareToken").asText();
        assertStatus(patch(shares(id) + "/" + s.path("id").asText()).bearer(a.accessToken()).json(obj("role", "view")).send(), 200);
        assertError(updateDiagram(Actor.shareOnly(token), id, "T", "UML", d.path("version").asInt(), dataWith("X"), null), 403);
        assertError(post(API + "/diagrams/" + id + "/versions").share(token).json(obj("note", "x")).send(), 403);
        // still viewable
        assertStatus(get(API + "/diagrams/" + id).share(token).send(), 200);
    }

    @Test
    void updateShareInvalidRoleReturns400() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        JsonNode s = createShare(a, id, "view");
        assertError(patch(shares(id) + "/" + s.path("id").asText()).bearer(a.accessToken()).json(obj("role", "superuser")).send(), 400);
    }

    @Test
    void updateShareMissingRoleReturns400() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        JsonNode s = createShare(a, id, "view");
        assertError(patch(shares(id) + "/" + s.path("id").asText()).bearer(a.accessToken()).json(obj()).send(), 400);
    }

    @Test
    void updateShareOtherUserReturns403AndKeepsRole() {
        Actor a = registerAndLogin();
        Actor b = registerAndLogin();
        String id = createDiagramId(a);
        JsonNode s = createShare(a, id, "view");
        assertError(patch(shares(id) + "/" + s.path("id").asText()).bearer(b.accessToken()).json(obj("role", "edit")).send(), 403);
        assertThat(get(API + "/shares/" + s.path("shareToken").asText()).send().json().path("role").asText()).isEqualTo("view");
    }

    @Test
    void updateShareViewTokenHolderCannotEscalateOwnLink() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        JsonNode s = createShare(a, id, "view");
        String token = s.path("shareToken").asText();
        Res r = patch(shares(id) + "/" + s.path("id").asText()).share(token).json(obj("role", "edit")).send();
        assertStatusIn(r, 401, 403);
        assertThat(get(API + "/shares/" + token).send().json().path("role").asText()).isEqualTo("view");
    }

    @Test
    void updateShareEditTokenHolderIsDenied() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        JsonNode view = createShare(a, id, "view");
        String editToken = shareToken(a, id, "edit");
        Res r = patch(shares(id) + "/" + view.path("id").asText()).share(editToken).json(obj("role", "edit")).send();
        assertStatusIn(r, 401, 403);
    }

    @Test
    void updateShareAnonymousReturns401() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        JsonNode s = createShare(a, id, "view");
        assertError(patch(shares(id) + "/" + s.path("id").asText()).json(obj("role", "edit")).send(), 401);
    }

    @Test
    void updateShareUnknownShareReturns404() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        assertError(patch(shares(id) + "/" + UUID.randomUUID()).bearer(a.accessToken()).json(obj("role", "edit")).send(), 404);
    }

    @Test
    void updateShareBelongingToOtherDiagramReturns404AndKeepsRole() {
        Actor a = registerAndLogin();
        String d1 = createDiagramId(a);
        String d2 = createDiagramId(a);
        JsonNode s = createShare(a, d1, "view");
        assertError(patch(shares(d2) + "/" + s.path("id").asText()).bearer(a.accessToken()).json(obj("role", "edit")).send(), 404);
        assertThat(get(API + "/shares/" + s.path("shareToken").asText()).send().json().path("role").asText()).isEqualTo("view");
    }

    @Test
    void updateShareOfOtherOwnersDiagramViaOwnDiagramPathIsDenied() {
        // B owns d2; tries to modify A's share by putting it under d2's path.
        Actor a = registerAndLogin();
        Actor b = registerAndLogin();
        String aDiagram = createDiagramId(a);
        String bDiagram = createDiagramId(b);
        JsonNode s = createShare(a, aDiagram, "view");
        Res r = patch(shares(bDiagram) + "/" + s.path("id").asText()).bearer(b.accessToken()).json(obj("role", "edit")).send();
        assertStatusIn(r, 403, 404);
        assertThat(get(API + "/shares/" + s.path("shareToken").asText()).send().json().path("role").asText()).isEqualTo("view");
    }

    // ------------------------------------------------------------------ DELETE share (revoke)

    @Test
    void revokeShareOwnerInvalidatesTokenImmediately() {
        Actor a = registerAndLogin();
        JsonNode d = createDiagram(a, "UML");
        String id = d.path("id").asText();
        JsonNode s = createShare(a, id, "edit");
        String token = s.path("shareToken").asText();
        assertStatus(updateDiagram(Actor.shareOnly(token), id, "T", "UML", d.path("version").asInt(), dataWith("E1"), null), 200);

        Res r = delete(shares(id) + "/" + s.path("id").asText()).bearer(a.accessToken()).send();
        // SPEC-AMBIGUOUS: openapi lists no success code; 204 No Content is the conventional one (200 also accepted).
        assertStatusIn(r, 200, 204);

        Res open = get(API + "/shares/" + token).send();
        // Spec rule 4: revoked or unknown -> 404 Not Found / 410 Gone
        assertStatusIn(open, 404, 410);
        assertErrorBody(open);
        // header no longer grants anything
        assertStatusIn(get(API + "/diagrams/" + id).share(token).send(), 401, 403);
        assertError(updateDiagram(Actor.shareOnly(token), id, "T", "UML", d.path("version").asInt() + 1, dataWith("E2"), null), 401);
        assertError(get(API + "/diagrams/" + id + "/versions").share(token).send(), 401);
        assertThat(getDiagram(a, id).path("data")).isEqualTo(dataWith("E1"));
        assertThat(items(get(shares(id)).bearer(a.accessToken()).send().json())).isEmpty();
    }

    @Test
    void updateShareAfterRevokeReturns404AndLinkStaysRevoked() {
        Actor a = registerAndLogin();
        JsonNode d = createDiagram(a, "UML");
        String id = d.path("id").asText();
        JsonNode s = createShare(a, id, "view");
        String token = s.path("shareToken").asText();
        String shareUrl = shares(id) + "/" + s.path("id").asText();
        assertStatus(delete(shareUrl).bearer(a.accessToken()).send(), 204);

        assertError(patch(shareUrl).bearer(a.accessToken()).json(obj("role", "edit")).send(), 404);

        // the PATCH must not resurrect the link (with either role)
        assertStatusIn(get(API + "/shares/" + token).send(), 404, 410);
        assertError(updateDiagram(Actor.shareOnly(token), id, "T", "UML", d.path("version").asInt(), dataWith("X"), null), 401);
        assertThat(items(get(shares(id)).bearer(a.accessToken()).send().json())).isEmpty();
        assertThat(getDiagram(a, id).path("data")).isEqualTo(emptyData());
    }

    @Test
    void concurrentRevokeAndUpdateNeverResurrectsLink() throws Exception {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 5; round++) {
                JsonNode s = createShare(a, id, "view");
                String url = shares(id) + "/" + s.path("id").asText();
                CountDownLatch start = new CountDownLatch(1);
                Future<Res> del = pool.submit(() -> {
                    start.await();
                    return delete(url).bearer(a.accessToken()).send();
                });
                Future<Res> upd = pool.submit(() -> {
                    start.await();
                    return patch(url).bearer(a.accessToken()).json(obj("role", "edit")).send();
                });
                start.countDown();
                assertStatus(del.get(), 204);
                Res u = upd.get();
                assertThat(u.status()).as("PATCH racing revoke: %s", u.body()).isIn(200, 404);
                assertStatusIn(get(API + "/shares/" + s.path("shareToken").asText()).send(), 404, 410);
            }
        } finally {
            pool.shutdown();
        }
        assertThat(items(get(shares(id)).bearer(a.accessToken()).send().json())).isEmpty();
    }

    @Test
    void revokeShareOnlyAffectsThatShare() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        JsonNode s1 = createShare(a, id, "view");
        String t2 = shareToken(a, id, "view");
        assertStatusIn(delete(shares(id) + "/" + s1.path("id").asText()).bearer(a.accessToken()).send(), 200, 204);
        assertStatus(get(API + "/shares/" + t2).send(), 200);
    }

    @Test
    void revokeShareTwiceReturns404() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        JsonNode s = createShare(a, id, "view");
        assertStatusIn(delete(shares(id) + "/" + s.path("id").asText()).bearer(a.accessToken()).send(), 200, 204);
        assertError(delete(shares(id) + "/" + s.path("id").asText()).bearer(a.accessToken()).send(), 404);
    }

    @Test
    void revokeShareOtherUserReturns403AndTokenStillWorks() {
        Actor a = registerAndLogin();
        Actor b = registerAndLogin();
        String id = createDiagramId(a);
        JsonNode s = createShare(a, id, "view");
        assertError(delete(shares(id) + "/" + s.path("id").asText()).bearer(b.accessToken()).send(), 403);
        assertStatus(get(API + "/shares/" + s.path("shareToken").asText()).send(), 200);
    }

    @Test
    void revokeShareEditTokenHolderIsDenied() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        JsonNode s = createShare(a, id, "edit");
        String token = s.path("shareToken").asText();
        assertStatusIn(delete(shares(id) + "/" + s.path("id").asText()).share(token).send(), 401, 403);
        assertStatus(get(API + "/shares/" + token).send(), 200);
    }

    @Test
    void revokeShareAnonymousReturns401() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        JsonNode s = createShare(a, id, "view");
        assertError(delete(shares(id) + "/" + s.path("id").asText()).send(), 401);
        assertStatus(get(API + "/shares/" + s.path("shareToken").asText()).send(), 200);
    }

    @Test
    void revokeShareUnknownShareReturns404() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        assertError(delete(shares(id) + "/" + UUID.randomUUID()).bearer(a.accessToken()).send(), 404);
    }

    @Test
    void revokeShareBelongingToOtherDiagramReturns404AndTokenStillWorks() {
        Actor a = registerAndLogin();
        String d1 = createDiagramId(a);
        String d2 = createDiagramId(a);
        JsonNode s = createShare(a, d1, "view");
        assertError(delete(shares(d2) + "/" + s.path("id").asText()).bearer(a.accessToken()).send(), 404);
        assertStatus(get(API + "/shares/" + s.path("shareToken").asText()).send(), 200);
    }

    @Test
    void revokeShareOfOtherOwnersDiagramViaOwnDiagramPathIsDenied() {
        Actor a = registerAndLogin();
        Actor b = registerAndLogin();
        String aDiagram = createDiagramId(a);
        String bDiagram = createDiagramId(b);
        JsonNode s = createShare(a, aDiagram, "view");
        Res r = delete(shares(bDiagram) + "/" + s.path("id").asText()).bearer(b.accessToken()).send();
        assertStatusIn(r, 403, 404);
        assertStatus(get(API + "/shares/" + s.path("shareToken").asText()).send(), 200);
    }

    @Test
    void revokeShareMalformedShareIdReturns400() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        assertError(delete(shares(id) + "/nope").bearer(a.accessToken()).send(), 400);
    }

    // ------------------------------------------------------------------ GET /shares/{token}

    @Test
    void openShareTokenAnonymousReturnsDiagramAndRole() {
        Actor a = registerAndLogin();
        JsonNode d = createDiagram(a, "ERD", "Shared ERD");
        String id = d.path("id").asText();
        setData(a, id, dataWith("SH"));
        String token = shareToken(a, id, "view");
        Res r = get(API + "/shares/" + token).send();
        assertStatus(r, 200);
        JsonNode j = r.json();
        assertThat(j.path("id").asText()).isEqualTo(id);
        assertThat(j.path("title").asText()).isEqualTo("Shared ERD");
        assertThat(j.path("diagramType").asText()).isEqualTo("ERD");
        assertThat(j.path("role").asText()).isEqualTo("view");
        assertThat(j.path("version").isInt()).isTrue();
        assertThat(j.path("isPublic").isBoolean()).isTrue();
        assertThat(j.path("createdAt").isTextual()).isTrue();
        assertThat(j.path("updatedAt").isTextual()).isTrue();
        // SPEC-AMBIGUOUS: openapi types `data` as string; we require a JSON object.
        assertThat(j.path("data").isObject()).as("data object in %s", j).isTrue();
        assertThat(j.path("data")).isEqualTo(dataWith("SH"));
        // public response must not leak other share tokens or owner credentials
        assertThat(r.body()).doesNotContain("password").doesNotContain("refreshToken");
    }

    @Test
    void openShareTokenEditRoleReportsEdit() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        String token = shareToken(a, id, "edit");
        Res r = get(API + "/shares/" + token).send();
        assertStatus(r, 200);
        assertThat(r.json().path("role").asText()).isEqualTo("edit");
    }

    @Test
    void openShareTokenReflectsLatestDiagramState() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        String token = shareToken(a, id, "view");
        JsonNode u = setData(a, id, dataWith("LATEST"));
        JsonNode j = get(API + "/shares/" + token).send().json();
        assertThat(j.path("data")).isEqualTo(dataWith("LATEST"));
        assertThat(j.path("version").asInt()).isEqualTo(u.path("version").asInt());
    }

    @Test
    void openShareTokenUnknownReturns404() {
        Res r = get(API + "/shares/" + "0".repeat(48)).send();
        assertStatusIn(r, 404, 410);
        assertErrorBody(r);
    }

    @Test
    void openShareTokenLoggedInOtherUserReturns200() {
        Actor a = registerAndLogin();
        Actor b = registerAndLogin();
        String id = createDiagramId(a);
        String token = shareToken(a, id, "view");
        assertStatus(get(API + "/shares/" + token).bearer(b.accessToken()).send(), 200);
    }

    @Test
    void openShareTokenWithInvalidBearerStillReturns200() {
        // Public endpoint: an invalid/expired JWT should not block opening a valid share link.
        // SPEC-AMBIGUOUS: some APIs reject invalid credentials even on public endpoints.
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        String token = shareToken(a, id, "view");
        assertStatus(get(API + "/shares/" + token).bearer("garbage").send(), 200);
    }

    // ------------------------------------------------------------------ owner + own link

    @Test
    void ownerUsingOwnViewLinkKeepsOwnerRights() {
        Actor a = registerAndLogin();
        JsonNode d = createDiagram(a, "UML");
        String id = d.path("id").asText();
        String token = shareToken(a, id, "view");
        Actor ownerWithLink = a.withShare(token);
        assertStatus(updateDiagram(ownerWithLink, id, "T", "UML", d.path("version").asInt(), dataWith("O"), true), 200);
        assertStatus(get(shares(id)).as(ownerWithLink).send(), 200);
    }
}
