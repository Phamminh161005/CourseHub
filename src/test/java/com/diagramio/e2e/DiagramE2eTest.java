package com.diagramio.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class DiagramE2eTest extends E2eTestBase {

    private static final String[] SUMMARY_FIELDS =
            {"id", "title", "diagramType", "version", "isPublic", "ownerId", "createdAt", "updatedAt"};

    private static void assertDiagramShape(JsonNode d) {
        assertUuid(d.path("id"));
        assertThat(d.path("title").isTextual()).isTrue();
        assertThat(d.path("diagramType").asText()).isIn("UML", "ERD");
        assertThat(d.path("version").isInt()).as("version int in %s", d).isTrue();
        assertThat(d.path("isPublic").isBoolean()).as("isPublic boolean in %s", d).isTrue();
        assertUuid(d.path("ownerId"));
        assertThat(d.path("createdAt").isTextual()).isTrue();
        assertThat(d.path("updatedAt").isTextual()).isTrue();
        // SPEC-AMBIGUOUS: openapi types `data` as string but its example is an object; the spec says "data as JSON"
        // with nodes/edges, so we require a real JSON object, not a JSON-encoded string.
        assertThat(d.path("data").isObject()).as("data must be a JSON object in %s", d).isTrue();
    }

    // ------------------------------------------------------------------ POST /diagrams

    @Test
    void createDiagramAuthenticatedReturns201PrivateWithEmptyData() {
        Actor a = registerAndLogin();
        Res r = post(API + "/diagrams").bearer(a.accessToken()).json(obj("title", "Shop ERD", "diagramType", "ERD")).send();
        assertStatus(r, 201);
        JsonNode d = r.json();
        assertDiagramShape(d);
        assertThat(d.path("title").asText()).isEqualTo("Shop ERD");
        assertThat(d.path("diagramType").asText()).isEqualTo("ERD");
        assertThat(d.path("isPublic").asBoolean()).isFalse();
        assertThat(d.path("ownerId").asText()).isEqualTo(a.userId());
        assertThat(d.path("data")).isEqualTo(emptyData());
    }

    @Test
    void createDiagramUmlTypeReturns201() {
        Actor a = registerAndLogin();
        JsonNode d = createDiagram(a, "UML");
        assertThat(d.path("diagramType").asText()).isEqualTo("UML");
    }

    @Test
    void createDiagramIgnoresClientSuppliedDataAndIsPublic() {
        Actor a = registerAndLogin();
        ObjectNode body = obj("title", "Sneaky", "diagramType", "UML", "isPublic", true);
        body.set("data", dataWith("X"));
        Res r = post(API + "/diagrams").bearer(a.accessToken()).json(body).send();
        // Either rejected or created with defaults; it must never be public or carry client data.
        if (r.status() == 201) {
            assertThat(r.json().path("isPublic").asBoolean()).isFalse();
            assertThat(r.json().path("data")).isEqualTo(emptyData());
            JsonNode stored = getDiagram(a, r.json().path("id").asText());
            assertThat(stored.path("isPublic").asBoolean()).isFalse();
            assertThat(stored.path("data")).isEqualTo(emptyData());
        } else {
            assertError(r, 400);
        }
    }

    @Test
    void createDiagramAnonymousReturns401() {
        assertError(post(API + "/diagrams").json(obj("title", "x", "diagramType", "UML")).send(), 401);
    }

    @Test
    void createDiagramWithOnlyShareTokenReturns401() {
        Actor owner = registerAndLogin();
        String token = shareToken(owner, createDiagramId(owner), "edit");
        Res r = post(API + "/diagrams").share(token).json(obj("title", "x", "diagramType", "UML")).send();
        // Only authenticated users can create; a share link is not an account.
        assertStatusIn(r, 401, 403);
    }

    @Test
    void createDiagramInvalidTypeReturns400() {
        Actor a = registerAndLogin();
        assertError(post(API + "/diagrams").bearer(a.accessToken()).json(obj("title", "x", "diagramType", "FLOWCHART")).send(), 400);
    }

    @Test
    void createDiagramMissingTypeReturns400() {
        Actor a = registerAndLogin();
        assertError(post(API + "/diagrams").bearer(a.accessToken()).json(obj("title", "x")).send(), 400);
    }

    @Test
    void createDiagramEmptyTitleReturns400() {
        Actor a = registerAndLogin();
        assertError(post(API + "/diagrams").bearer(a.accessToken()).json(obj("title", "", "diagramType", "UML")).send(), 400);
    }

    @Test
    void createDiagramBlankTitleReturns400() {
        Actor a = registerAndLogin();
        assertError(post(API + "/diagrams").bearer(a.accessToken()).json(obj("title", "   ", "diagramType", "UML")).send(), 400);
    }

    @Test
    void createDiagramMissingTitleReturns400() {
        // SPEC-AMBIGUOUS: openapi only marks diagramType as required, but business rule 1 says
        // "validate input (non-empty name, valid type) ... else 400". We follow the business rule.
        Actor a = registerAndLogin();
        assertError(post(API + "/diagrams").bearer(a.accessToken()).json(obj("diagramType", "UML")).send(), 400);
    }

    @Test
    void createDiagramTooLongTitleReturns400() {
        // title column is VARCHAR(255); a longer title is invalid input, not a 500.
        Actor a = registerAndLogin();
        Res r = post(API + "/diagrams").bearer(a.accessToken()).json(obj("title", "t".repeat(256), "diagramType", "UML")).send();
        assertError(r, 400);
    }

    @Test
    void createDiagramMalformedJsonReturns400() {
        Actor a = registerAndLogin();
        assertError(post(API + "/diagrams").bearer(a.accessToken()).rawJson("{\"title\":").send(), 400);
    }

    // ------------------------------------------------------------------ GET /diagrams (mine)

    @Test
    void listMyDiagramsReturnsPageOfOwnDiagramsWithoutData() {
        Actor a = registerAndLogin();
        Actor b = registerAndLogin();
        String mine = createDiagramId(a);
        String theirs = createDiagramId(b);
        Res r = get(API + "/diagrams").bearer(a.accessToken()).send();
        assertStatus(r, 200);
        JsonNode page = r.json();
        assertThat(page.path("content").isArray()).isTrue();
        assertThat(page.path("page").isInt()).isTrue();
        assertThat(page.path("size").isInt()).isTrue();
        assertThat(page.path("totalElements").isNumber()).isTrue();
        assertThat(page.path("totalPages").isInt()).isTrue();
        assertThat(page.path("totalElements").asLong()).isEqualTo(1);
        List<JsonNode> items = items(page);
        assertThat(containsId(items, mine)).isTrue();
        assertThat(containsId(items, theirs)).isFalse();
        for (JsonNode n : items) {
            for (String f : SUMMARY_FIELDS) {
                assertThat(n.has(f)).as("summary field %s in %s", f, n).isTrue();
            }
            assertThat(n.has("data")).as("list items must not include data").isFalse();
        }
    }

    @Test
    void listMyDiagramsFilterByTypeReturnsOnlyThatType() {
        Actor a = registerAndLogin();
        String uml = createDiagram(a, "UML").path("id").asText();
        String erd = createDiagram(a, "ERD").path("id").asText();
        Res r = get(API + "/diagrams?diagramType=ERD").bearer(a.accessToken()).send();
        assertStatus(r, 200);
        List<JsonNode> items = items(r.json());
        assertThat(containsId(items, erd)).isTrue();
        assertThat(containsId(items, uml)).isFalse();
        assertThat(items).allMatch(n -> n.path("diagramType").asText().equals("ERD"));
    }

    @Test
    void listMyDiagramsInvalidTypeFilterReturns400() {
        Actor a = registerAndLogin();
        assertError(get(API + "/diagrams?diagramType=PIE").bearer(a.accessToken()).send(), 400);
    }

    @Test
    void listMyDiagramsSearchByTitleIsCaseInsensitiveSubstring() {
        Actor a = registerAndLogin();
        String hit = createDiagram(a, "UML", "Library Management System").path("id").asText();
        String miss = createDiagram(a, "UML", "Shop ERD").path("id").asText();
        Res r = get(API + "/diagrams?title=libRARY").bearer(a.accessToken()).send();
        assertStatus(r, 200);
        List<JsonNode> items = items(r.json());
        assertThat(containsId(items, hit)).isTrue();
        assertThat(containsId(items, miss)).isFalse();
    }

    @Test
    void listMyDiagramsPaginationRespectsPageAndSize() {
        Actor a = registerAndLogin();
        for (int i = 0; i < 3; i++) {
            createDiagramId(a);
        }
        Res p0 = get(API + "/diagrams?page=0&size=2").bearer(a.accessToken()).send();
        Res p1 = get(API + "/diagrams?page=1&size=2").bearer(a.accessToken()).send();
        assertStatus(p0, 200);
        assertStatus(p1, 200);
        assertThat(p0.json().path("content").size()).isEqualTo(2);
        assertThat(p1.json().path("content").size()).isEqualTo(1);
        assertThat(p0.json().path("totalElements").asLong()).isEqualTo(3);
        assertThat(p0.json().path("totalPages").asInt()).isEqualTo(2);
        assertThat(p0.json().path("page").asInt()).isEqualTo(0);
        assertThat(p1.json().path("page").asInt()).isEqualTo(1);
        assertThat(p0.json().path("size").asInt()).isEqualTo(2);
    }

    @Test
    void listMyDiagramsSizeOver100Returns400() {
        Actor a = registerAndLogin();
        assertError(get(API + "/diagrams?size=101").bearer(a.accessToken()).send(), 400);
    }

    @Test
    void listMyDiagramsNonNumericPageReturns400() {
        Actor a = registerAndLogin();
        assertError(get(API + "/diagrams?page=abc").bearer(a.accessToken()).send(), 400);
    }

    @Test
    void listMyDiagramsAnonymousReturns401() {
        assertError(get(API + "/diagrams").send(), 401);
    }

    // SPEC-AMBIGUOUS: spec says "list diagrams I own or that are shared with me", but shares are anonymous
    // links not bound to a user, so there is no "shared with me" relation to test. Only ownership is asserted.

    // ------------------------------------------------------------------ GET /diagrams/public

    @Test
    void listPublicDiagramsAnonymousReturnsOnlyPublicWithoutData() {
        Actor a = registerAndLogin();
        String pub = createDiagramId(a);
        String priv = createDiagramId(a);
        setPublic(a, pub, true);
        Res first = get(API + "/diagrams/public").send();
        assertStatus(first, 200);
        assertThat(first.json().path("content").isArray()).isTrue();
        assertThat(first.json().has("totalElements")).isTrue();
        List<JsonNode> all = allPages(Actor.anonymous(), API + "/diagrams/public");
        assertThat(containsId(all, pub)).isTrue();
        assertThat(containsId(all, priv)).isFalse();
        assertThat(all).allMatch(n -> n.path("isPublic").asBoolean());
        assertThat(all).noneMatch(n -> n.has("data"));
    }

    @Test
    void listPublicDiagramsAfterMadePrivateNoLongerListed() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        setPublic(a, id, true);
        assertThat(containsId(allPages(Actor.anonymous(), API + "/diagrams/public"), id)).isTrue();
        setPublic(a, id, false);
        assertThat(containsId(allPages(Actor.anonymous(), API + "/diagrams/public"), id)).isFalse();
    }

    @Test
    void listPublicDiagramsSizeOver100Returns400() {
        assertError(get(API + "/diagrams/public?size=101").send(), 400);
    }

    // ------------------------------------------------------------------ GET /diagrams/{id}

    @Test
    void getDiagramOwnerReturns200WithData() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        Res r = get(API + "/diagrams/" + id).bearer(a.accessToken()).send();
        assertStatus(r, 200);
        assertDiagramShape(r.json());
        assertThat(r.json().path("id").asText()).isEqualTo(id);
        assertThat(r.json().path("data")).isEqualTo(emptyData());
    }

    @Test
    void getPrivateDiagramOtherUserReturns403() {
        Actor a = registerAndLogin();
        Actor b = registerAndLogin();
        String id = createDiagramId(a);
        Res r = get(API + "/diagrams/" + id).bearer(b.accessToken()).send();
        assertError(r, 403);
        assertThat(r.body()).doesNotContain("\"nodes\"");
    }

    @Test
    void getPrivateDiagramAnonymousIsDenied() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        Res r = get(API + "/diagrams/" + id).send();
        // SPEC-AMBIGUOUS: spec "otherwise Authenticated" suggests 401; flow doc says 403 for an anonymous caller.
        assertStatusIn(r, 401, 403);
        assertErrorBody(r);
        assertThat(r.body()).doesNotContain("\"nodes\"");
    }

    @Test
    void getPublicDiagramAnonymousReturns200() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        setData(a, id, dataWith("PUB"));
        setPublic(a, id, true);
        Res r = get(API + "/diagrams/" + id).send();
        assertStatus(r, 200);
        assertThat(r.json().path("data")).isEqualTo(dataWith("PUB"));
    }

    @Test
    void getPublicDiagramOtherUserReturns200() {
        Actor a = registerAndLogin();
        Actor b = registerAndLogin();
        String id = createDiagramId(a);
        setPublic(a, id, true);
        assertStatus(get(API + "/diagrams/" + id).bearer(b.accessToken()).send(), 200);
    }

    @Test
    void getPrivateDiagramViewShareTokenReturns200() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        String token = shareToken(a, id, "view");
        Res r = get(API + "/diagrams/" + id).share(token).send();
        assertStatus(r, 200);
        assertThat(r.json().path("id").asText()).isEqualTo(id);
    }

    @Test
    void getPrivateDiagramEditShareTokenReturns200() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        String token = shareToken(a, id, "edit");
        assertStatus(get(API + "/diagrams/" + id).share(token).send(), 200);
    }

    @Test
    void getPrivateDiagramOtherUserWithViewShareTokenReturns200() {
        Actor a = registerAndLogin();
        Actor b = registerAndLogin();
        String id = createDiagramId(a);
        String token = shareToken(a, id, "view");
        assertStatus(get(API + "/diagrams/" + id).as(b.withShare(token)).send(), 200);
    }

    @Test
    void getPrivateDiagramWithShareTokenOfOtherDiagramIsDenied() {
        Actor a = registerAndLogin();
        String target = createDiagramId(a);
        String other = createDiagramId(a);
        String tokenOfOther = shareToken(a, other, "edit");
        Res r = get(API + "/diagrams/" + target).share(tokenOfOther).send();
        // Flow doc: "Link của sơ đồ A không có tác dụng gì với sơ đồ B" -> 403 (caller known, no right)
        assertStatusIn(r, 401, 403);
        assertThat(r.body()).doesNotContain("\"nodes\"");
    }

    @Test
    void getDiagramUnknownIdReturns404() {
        Actor a = registerAndLogin();
        assertError(get(API + "/diagrams/" + UUID.randomUUID()).bearer(a.accessToken()).send(), 404);
    }

    @Test
    void getDiagramMalformedIdReturns400() {
        Actor a = registerAndLogin();
        assertError(get(API + "/diagrams/not-a-uuid").bearer(a.accessToken()).send(), 400);
    }

    // ------------------------------------------------------------------ PUT /diagrams/{id}

    @Test
    void updateDiagramOwnerReturns200AndIncrementsVersion() {
        Actor a = registerAndLogin();
        JsonNode d = createDiagram(a, "UML", "Before");
        String id = d.path("id").asText();
        int v = d.path("version").asInt();
        Res r = updateDiagram(a, id, "After", "ERD", v, dataWith("N1"), false);
        assertStatus(r, 200);
        JsonNode u = r.json();
        assertDiagramShape(u);
        assertThat(u.path("version").asInt()).isEqualTo(v + 1);
        assertThat(u.path("title").asText()).isEqualTo("After");
        assertThat(u.path("diagramType").asText()).isEqualTo("ERD");
        assertThat(u.path("data")).isEqualTo(dataWith("N1"));
        assertThat(u.path("updatedBy").asText()).isEqualTo(a.userId());
        JsonNode stored = getDiagram(a, id);
        assertThat(stored.path("data")).isEqualTo(dataWith("N1"));
        assertThat(stored.path("version").asInt()).isEqualTo(v + 1);
    }

    @Test
    void updateDiagramStaleVersionReturns409AndLeavesDataUnchanged() {
        Actor a = registerAndLogin();
        JsonNode d = createDiagram(a, "UML", "T");
        String id = d.path("id").asText();
        int v = d.path("version").asInt();
        assertStatus(updateDiagram(a, id, "T1", "UML", v, dataWith("FIRST"), false), 200);
        Res stale = updateDiagram(a, id, "T2", "ERD", v, dataWith("SECOND"), true);
        assertError(stale, 409);
        JsonNode stored = getDiagram(a, id);
        assertThat(stored.path("data")).isEqualTo(dataWith("FIRST"));
        assertThat(stored.path("title").asText()).isEqualTo("T1");
        assertThat(stored.path("diagramType").asText()).isEqualTo("UML");
        assertThat(stored.path("isPublic").asBoolean()).isFalse();
        assertThat(stored.path("version").asInt()).isEqualTo(v + 1);
    }

    @Test
    void updateDiagramFutureVersionReturns409() {
        Actor a = registerAndLogin();
        JsonNode d = createDiagram(a, "UML");
        String id = d.path("id").asText();
        assertError(updateDiagram(a, id, "T", "UML", d.path("version").asInt() + 5, dataWith("X"), false), 409);
        assertThat(getDiagram(a, id).path("data")).isEqualTo(emptyData());
    }

    @Test
    void updateDiagramConcurrentSameVersionOnlyOneSucceeds() throws Exception {
        Actor a = registerAndLogin();
        JsonNode d = createDiagram(a, "UML");
        String id = d.path("id").asText();
        int v = d.path("version").asInt();
        int n = 6;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Res>> fs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            final int k = i;
            fs.add(pool.submit(() -> {
                start.await();
                return updateDiagram(a, id, "T", "UML", v, dataWith("W" + k), false);
            }));
        }
        start.countDown();
        int ok = 0;
        int conflict = 0;
        for (Future<Res> f : fs) {
            Res r = f.get();
            if (r.status() == 200) {
                ok++;
            } else if (r.status() == 409) {
                conflict++;
            } else {
                throw new AssertionError("unexpected " + r);
            }
        }
        pool.shutdown();
        assertThat(ok).isEqualTo(1);
        assertThat(conflict).isEqualTo(n - 1);
        assertThat(getDiagram(a, id).path("version").asInt()).isEqualTo(v + 1);
    }

    @Test
    void updateDiagramMissingVersionReturns400() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        ObjectNode body = obj("title", "T", "diagramType", "UML");
        body.set("data", dataWith("X"));
        assertError(put(API + "/diagrams/" + id).bearer(a.accessToken()).json(body).send(), 400);
        assertThat(getDiagram(a, id).path("data")).isEqualTo(emptyData());
    }

    @Test
    void updateDiagramMissingDataReturns400() {
        Actor a = registerAndLogin();
        JsonNode d = createDiagram(a, "UML");
        Res r = put(API + "/diagrams/" + d.path("id").asText()).bearer(a.accessToken())
                .json(obj("title", "T", "diagramType", "UML", "version", d.path("version").asInt())).send();
        assertError(r, 400);
    }

    @Test
    void updateDiagramInvalidTypeReturns400() {
        Actor a = registerAndLogin();
        JsonNode d = createDiagram(a, "UML");
        assertError(updateDiagram(a, d.path("id").asText(), "T", "BPMN", d.path("version").asInt(), dataWith("X"), false), 400);
    }

    @Test
    void updateDiagramBlankTitleReturns400() {
        Actor a = registerAndLogin();
        JsonNode d = createDiagram(a, "UML");
        assertError(updateDiagram(a, d.path("id").asText(), "", "UML", d.path("version").asInt(), dataWith("X"), false), 400);
    }

    @Test
    void updateDiagramDataWithoutNodesReturns400AndLeavesDataUnchanged() {
        Actor a = registerAndLogin();
        JsonNode d = createDiagram(a, "UML");
        String id = d.path("id").asText();
        Res r = updateDiagram(a, id, "T", "UML", d.path("version").asInt(), parse("{\"edges\":[]}"), false);
        assertError(r, 400);
        JsonNode stored = getDiagram(a, id);
        assertThat(stored.path("data")).isEqualTo(emptyData());
        assertThat(stored.path("version").asInt()).isEqualTo(d.path("version").asInt());
    }

    @Test
    void updateDiagramDataWithoutEdgesReturns400() {
        Actor a = registerAndLogin();
        JsonNode d = createDiagram(a, "UML");
        assertError(updateDiagram(a, d.path("id").asText(), "T", "UML", d.path("version").asInt(),
                parse("{\"nodes\":[]}"), false), 400);
    }

    @Test
    void updateDiagramNodesNotArrayReturns400() {
        Actor a = registerAndLogin();
        JsonNode d = createDiagram(a, "UML");
        assertError(updateDiagram(a, d.path("id").asText(), "T", "UML", d.path("version").asInt(),
                parse("{\"nodes\":\"x\",\"edges\":[]}"), false), 400);
    }

    @Test
    void updateDiagramDataIsArrayReturns400() {
        Actor a = registerAndLogin();
        JsonNode d = createDiagram(a, "UML");
        assertError(updateDiagram(a, d.path("id").asText(), "T", "UML", d.path("version").asInt(),
                parse("[1,2,3]"), false), 400);
    }

    @Test
    void updateDiagramDataIsStringReturns400() {
        Actor a = registerAndLogin();
        JsonNode d = createDiagram(a, "UML");
        assertError(updateDiagram(a, d.path("id").asText(), "T", "UML", d.path("version").asInt(),
                JSON.valueToTree("{\"nodes\":[],\"edges\":[]}"), false), 400);
    }

    @Test
    void updateDiagramOversizeDataReturns400AndLeavesDataUnchanged() {
        Actor a = registerAndLogin();
        JsonNode d = createDiagram(a, "UML");
        String id = d.path("id").asText();
        String big = "x".repeat(2 * 1024 * 1024 + 1024);
        JsonNode data = parse("{\"nodes\":[{\"id\":\"n\",\"label\":\"" + big + "\"}],\"edges\":[]}");
        Res r = updateDiagram(a, id, "T", "UML", d.path("version").asInt(), data, false);
        // Spec rule 2: "400 invalid/oversize JSON"
        assertError(r, 400);
        JsonNode stored = getDiagram(a, id);
        assertThat(stored.path("data")).isEqualTo(emptyData());
        assertThat(stored.path("version").asInt()).isEqualTo(d.path("version").asInt());
    }

    @Test
    void updateDiagramJustUnder2MbIsAccepted() {
        Actor a = registerAndLogin();
        JsonNode d = createDiagram(a, "UML");
        String big = "y".repeat(1_900_000);
        JsonNode data = parse("{\"nodes\":[{\"id\":\"n\",\"label\":\"" + big + "\"}],\"edges\":[]}");
        Res r = updateDiagram(a, d.path("id").asText(), "T", "UML", d.path("version").asInt(), data, false);
        assertStatus(r, 200);
    }

    @Test
    void updateDiagramTooLongTitleReturns400AndLeavesDiagramUnchanged() {
        Actor a = registerAndLogin();
        JsonNode d = createDiagram(a, "UML", "Short");
        String id = d.path("id").asText();
        assertError(updateDiagram(a, id, "t".repeat(256), "UML", d.path("version").asInt(), dataWith("X"), false), 400);
        JsonNode stored = getDiagram(a, id);
        assertThat(stored.path("title").asText()).isEqualTo("Short");
        assertThat(stored.path("data")).isEqualTo(emptyData());
    }

    @Test
    void updateDiagramMalformedJsonReturns400() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        assertError(put(API + "/diagrams/" + id).bearer(a.accessToken()).rawJson("{\"title\": \"x\", ").send(), 400);
    }

    @Test
    void updateDiagramEditShareTokenReturns200WithNullUpdatedBy() {
        Actor a = registerAndLogin();
        JsonNode d = createDiagram(a, "UML");
        String id = d.path("id").asText();
        String token = shareToken(a, id, "edit");
        Res r = updateDiagram(Actor.shareOnly(token), id, "Edited", "UML", d.path("version").asInt(), dataWith("E"), null);
        assertStatus(r, 200);
        assertThat(r.json().path("data")).isEqualTo(dataWith("E"));
        assertThat(r.json().path("version").asInt()).isEqualTo(d.path("version").asInt() + 1);
        // flow doc: updatedBy = NULL when the editor only holds a link
        assertThat(r.json().path("updatedBy").isNull() || r.json().path("updatedBy").isMissingNode()).isTrue();
    }

    @Test
    void updateDiagramLoggedInEditorViaShareTokenRecordsUpdatedBy() {
        Actor a = registerAndLogin();
        Actor b = registerAndLogin();
        JsonNode d = createDiagram(a, "UML");
        String id = d.path("id").asText();
        String token = shareToken(a, id, "edit");
        Res r = updateDiagram(b.withShare(token), id, "T", "UML", d.path("version").asInt(), dataWith("B"), null);
        assertStatus(r, 200);
        assertThat(r.json().path("updatedBy").asText()).isEqualTo(b.userId());
        assertThat(r.json().path("ownerId").asText()).isEqualTo(a.userId());
    }

    @Test
    void updateDiagramEditorCannotChangeIsPublic() {
        Actor a = registerAndLogin();
        JsonNode d = createDiagram(a, "UML");
        String id = d.path("id").asText();
        String token = shareToken(a, id, "edit");
        Res r = updateDiagram(Actor.shareOnly(token), id, "T", "UML", d.path("version").asInt(), dataWith("E"), true);
        // README: "Chỉ chủ được đổi isPublic" (flow doc: editor's isPublic is ignored, kept as before)
        if (r.status() == 200) {
            assertThat(r.json().path("isPublic").asBoolean()).isFalse();
        } else {
            assertError(r, 403);
        }
        assertThat(getDiagram(a, id).path("isPublic").asBoolean()).isFalse();
        assertThat(containsId(allPages(Actor.anonymous(), API + "/diagrams/public"), id)).isFalse();
    }

    @Test
    void updateDiagramViewShareTokenReturns403AndLeavesDataUnchanged() {
        Actor a = registerAndLogin();
        JsonNode d = createDiagram(a, "UML");
        String id = d.path("id").asText();
        String token = shareToken(a, id, "view");
        Res r = updateDiagram(Actor.shareOnly(token), id, "T", "UML", d.path("version").asInt(), dataWith("V"), null);
        assertError(r, 403);
        assertThat(getDiagram(a, id).path("data")).isEqualTo(emptyData());
    }

    @Test
    void updateDiagramOtherUserReturns403AndLeavesDataUnchanged() {
        Actor a = registerAndLogin();
        Actor b = registerAndLogin();
        JsonNode d = createDiagram(a, "UML");
        String id = d.path("id").asText();
        assertError(updateDiagram(b, id, "T", "UML", d.path("version").asInt(), dataWith("B"), true), 403);
        JsonNode stored = getDiagram(a, id);
        assertThat(stored.path("data")).isEqualTo(emptyData());
        assertThat(stored.path("isPublic").asBoolean()).isFalse();
    }

    @Test
    void updatePublicDiagramOtherUserReturns403() {
        Actor a = registerAndLogin();
        Actor b = registerAndLogin();
        String id = createDiagramId(a);
        JsonNode d = setPublic(a, id, true);
        assertError(updateDiagram(b, id, "T", "UML", d.path("version").asInt(), dataWith("B"), true), 403);
    }

    @Test
    void updatePublicDiagramAnonymousReturns401() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        JsonNode d = setPublic(a, id, true);
        assertError(updateDiagram(Actor.anonymous(), id, "T", "UML", d.path("version").asInt(), dataWith("X"), true), 401);
        assertThat(getDiagram(a, id).path("data")).isEqualTo(emptyData());
    }

    @Test
    void updateDiagramAnonymousReturns401() {
        Actor a = registerAndLogin();
        JsonNode d = createDiagram(a, "UML");
        assertError(updateDiagram(Actor.anonymous(), d.path("id").asText(), "T", "UML",
                d.path("version").asInt(), dataWith("X"), false), 401);
    }

    @Test
    void updateDiagramInvalidShareTokenOnlyReturns401() {
        Actor a = registerAndLogin();
        JsonNode d = createDiagram(a, "UML");
        assertError(updateDiagram(Actor.shareOnly("deadbeef" + uniq()), d.path("id").asText(), "T", "UML",
                d.path("version").asInt(), dataWith("X"), false), 401);
    }

    @Test
    void updateDiagramEditTokenOfOtherDiagramIsDenied() {
        Actor a = registerAndLogin();
        JsonNode target = createDiagram(a, "UML");
        String tokenOfOther = shareToken(a, createDiagramId(a), "edit");
        Res r = updateDiagram(Actor.shareOnly(tokenOfOther), target.path("id").asText(), "T", "UML",
                target.path("version").asInt(), dataWith("X"), null);
        assertStatusIn(r, 401, 403);
        assertThat(getDiagram(a, target.path("id").asText()).path("data")).isEqualTo(emptyData());
    }

    @Test
    void updateDiagramUnknownIdReturns404() {
        Actor a = registerAndLogin();
        assertError(updateDiagram(a, UUID.randomUUID().toString(), "T", "UML", 1, dataWith("X"), false), 404);
    }

    @Test
    void updateDiagramOwnerCanMakePublic() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        JsonNode u = setPublic(a, id, true);
        assertThat(u.path("isPublic").asBoolean()).isTrue();
        assertStatus(get(API + "/diagrams/" + id).send(), 200);
    }

    // ------------------------------------------------------------------ DELETE /diagrams/{id}

    @Test
    void deleteDiagramOwnerReturns204ThenNotFound() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        Res r = delete(API + "/diagrams/" + id).bearer(a.accessToken()).send();
        assertStatus(r, 204);
        assertError(get(API + "/diagrams/" + id).bearer(a.accessToken()).send(), 404);
        assertThat(containsId(items(get(API + "/diagrams").bearer(a.accessToken()).send().json()), id)).isFalse();
    }

    @Test
    void deleteDiagramTwiceReturns404() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        assertStatus(delete(API + "/diagrams/" + id).bearer(a.accessToken()).send(), 204);
        assertError(delete(API + "/diagrams/" + id).bearer(a.accessToken()).send(), 404);
    }

    @Test
    void deleteDiagramOtherUserReturns403AndKeepsDiagram() {
        Actor a = registerAndLogin();
        Actor b = registerAndLogin();
        String id = createDiagramId(a);
        assertError(delete(API + "/diagrams/" + id).bearer(b.accessToken()).send(), 403);
        getDiagram(a, id);
    }

    @Test
    void deletePublicDiagramOtherUserReturns403() {
        Actor a = registerAndLogin();
        Actor b = registerAndLogin();
        String id = createDiagramId(a);
        setPublic(a, id, true);
        assertError(delete(API + "/diagrams/" + id).bearer(b.accessToken()).send(), 403);
        getDiagram(a, id);
    }

    @Test
    void deleteDiagramEditShareTokenIsDeniedAndKeepsDiagram() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        String token = shareToken(a, id, "edit");
        Res r = delete(API + "/diagrams/" + id).share(token).send();
        // Owner only. Whether a bare link yields 401 (endpoint requires login) or 403 is ambiguous.
        assertStatusIn(r, 401, 403);
        assertErrorBody(r);
        getDiagram(a, id);
    }

    @Test
    void deleteDiagramLoggedInUserWithEditShareTokenReturns403() {
        Actor a = registerAndLogin();
        Actor b = registerAndLogin();
        String id = createDiagramId(a);
        String token = shareToken(a, id, "edit");
        assertError(delete(API + "/diagrams/" + id).as(b.withShare(token)).send(), 403);
        getDiagram(a, id);
    }

    @Test
    void deleteDiagramAnonymousReturns401() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        assertError(delete(API + "/diagrams/" + id).send(), 401);
        getDiagram(a, id);
    }

    @Test
    void deleteDiagramUnknownIdReturns404() {
        Actor a = registerAndLogin();
        assertError(delete(API + "/diagrams/" + UUID.randomUUID()).bearer(a.accessToken()).send(), 404);
    }

    @Test
    void deleteDiagramMalformedIdReturns400() {
        Actor a = registerAndLogin();
        assertError(delete(API + "/diagrams/123").bearer(a.accessToken()).send(), 400);
    }

    @Test
    void deleteDiagramInvalidatesItsShareTokensAndVersions() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        String token = shareToken(a, id, "view");
        createVersion(a, id, "v1");
        assertStatus(delete(API + "/diagrams/" + id).bearer(a.accessToken()).send(), 204);
        assertStatusIn(get(API + "/shares/" + token).send(), 404, 410);
        assertError(get(API + "/diagrams/" + id + "/versions").bearer(a.accessToken()).send(), 404);
    }
}
