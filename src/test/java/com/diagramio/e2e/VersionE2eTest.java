package com.diagramio.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class VersionE2eTest extends E2eTestBase {

    private String versions(String diagramId) {
        return API + "/diagrams/" + diagramId + "/versions";
    }

    private static void assertVersionDetailShape(JsonNode v) {
        assertUuid(v.path("id"));
        assertUuid(v.path("diagramId"));
        assertThat(v.path("versionNumber").isInt()).as("versionNumber in %s", v).isTrue();
        assertThat(v.has("note")).isTrue();
        assertThat(v.path("createdAt").isTextual()).isTrue();
        // SPEC-AMBIGUOUS: openapi types `data` as string; we require a JSON object (see DiagramE2eTest).
        assertThat(v.path("data").isObject()).as("data object in %s", v).isTrue();
    }

    // ------------------------------------------------------------------ POST versions

    @Test
    void createVersionOwnerReturns201WithSnapshotOfCurrentData() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        setData(a, id, dataWith("S1"));
        Res r = post(versions(id)).bearer(a.accessToken()).json(obj("note", "Bản thiết kế ERD Pha 1")).send();
        assertStatus(r, 201);
        JsonNode v = r.json();
        assertVersionDetailShape(v);
        assertThat(v.path("diagramId").asText()).isEqualTo(id);
        assertThat(v.path("note").asText()).isEqualTo("Bản thiết kế ERD Pha 1");
        assertThat(v.path("data")).isEqualTo(dataWith("S1"));
        assertThat(v.path("versionNumber").asInt()).isEqualTo(1);
        assertThat(v.path("createdBy").asText()).isEqualTo(a.userId());
        String location = r.header("Location");
        assertThat(location).as("Location header").isNotNull().endsWith("/versions/" + v.path("id").asText());
    }

    @Test
    void createVersionNumbersIncrementPerDiagram() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        String other = createDiagramId(a);
        assertThat(createVersion(a, id, "a").path("versionNumber").asInt()).isEqualTo(1);
        assertThat(createVersion(a, id, "b").path("versionNumber").asInt()).isEqualTo(2);
        assertThat(createVersion(a, other, "c").path("versionNumber").asInt()).isEqualTo(1);
    }

    @Test
    void createVersionDoesNotChangeDiagram() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        JsonNode before = setData(a, id, dataWith("K"));
        createVersion(a, id, "snap");
        JsonNode after = getDiagram(a, id);
        assertThat(after.path("data")).isEqualTo(before.path("data"));
        assertThat(after.path("version").asInt()).isEqualTo(before.path("version").asInt());
    }

    @Test
    void createVersionEmptyBodyObjectReturns201() {
        // openapi: note has minLength 0 and the request body is optional.
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        assertStatus(post(versions(id)).bearer(a.accessToken()).json(obj()).send(), 201);
    }

    @Test
    void createVersionNoteOver255Returns400() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        assertError(post(versions(id)).bearer(a.accessToken()).json(obj("note", "n".repeat(256))).send(), 400);
        assertThat(items(get(versions(id)).bearer(a.accessToken()).send().json())).isEmpty();
    }

    @Test
    void createVersionMatchingClientVersionReturns201() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        JsonNode d = setData(a, id, dataWith("M"));
        Res r = post(versions(id)).bearer(a.accessToken()).json(obj("note", "x", "version", d.path("version").asInt())).send();
        assertStatus(r, 201);
    }

    @Test
    void createVersionStaleClientVersionReturns409() {
        Actor a = registerAndLogin();
        JsonNode d = createDiagram(a, "UML");
        String id = d.path("id").asText();
        setData(a, id, dataWith("NEW"));
        Res r = post(versions(id)).bearer(a.accessToken()).json(obj("note", "x", "version", d.path("version").asInt())).send();
        assertError(r, 409);
        assertThat(items(get(versions(id)).bearer(a.accessToken()).send().json())).isEmpty();
    }

    @Test
    void createVersionEditShareTokenReturns201WithNullCreatedBy() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        String token = shareToken(a, id, "edit");
        Res r = post(versions(id)).share(token).json(obj("note", "by link")).send();
        assertStatus(r, 201);
        assertThat(r.json().path("createdBy").isNull() || r.json().path("createdBy").isMissingNode()).isTrue();
    }

    @Test
    void createVersionViewShareTokenReturns403() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        String token = shareToken(a, id, "view");
        assertError(post(versions(id)).share(token).json(obj("note", "x")).send(), 403);
    }

    @Test
    void createVersionOtherUserReturns403() {
        Actor a = registerAndLogin();
        Actor b = registerAndLogin();
        String id = createDiagramId(a);
        assertError(post(versions(id)).bearer(b.accessToken()).json(obj("note", "x")).send(), 403);
    }

    @Test
    void createVersionOnPublicDiagramOtherUserReturns403() {
        Actor a = registerAndLogin();
        Actor b = registerAndLogin();
        String id = createDiagramId(a);
        setPublic(a, id, true);
        assertError(post(versions(id)).bearer(b.accessToken()).json(obj("note", "x")).send(), 403);
    }

    @Test
    void createVersionAnonymousReturns401() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        assertError(post(versions(id)).json(obj("note", "x")).send(), 401);
    }

    @Test
    void createVersionUnknownDiagramReturns404() {
        Actor a = registerAndLogin();
        assertError(post(versions(UUID.randomUUID().toString())).bearer(a.accessToken()).json(obj("note", "x")).send(), 404);
    }

    @Test
    void createVersionConcurrentRequestsGetDistinctNumbers() throws Exception {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        int n = 8;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Res>> fs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            final int k = i;
            fs.add(pool.submit(() -> {
                start.await();
                return post(versions(id)).bearer(a.accessToken()).json(obj("note", "c" + k)).send();
            }));
        }
        start.countDown();
        Set<Integer> numbers = new HashSet<>();
        for (Future<Res> f : fs) {
            Res r = f.get();
            assertThat(r.status()).as("concurrent save: %s", r.body()).isIn(201, 409);
            if (r.status() == 201) {
                assertThat(numbers.add(r.json().path("versionNumber").asInt())).isTrue();
            }
        }
        pool.shutdown();
        List<JsonNode> list = items(get(versions(id)).bearer(a.accessToken()).send().json());
        assertThat(list).hasSize(numbers.size());
    }

    // ------------------------------------------------------------------ GET versions (list)

    @Test
    void listVersionsOwnerReturnsNewestFirstWithoutData() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        createVersion(a, id, "first");
        createVersion(a, id, "second");
        createVersion(a, id, "third");
        Res r = get(versions(id)).bearer(a.accessToken()).send();
        assertStatus(r, 200);
        assertThat(r.json().isArray()).as("openapi: array of DiagramVersionSummaryResponse").isTrue();
        List<JsonNode> list = items(r.json());
        assertThat(list).hasSize(3);
        assertThat(list.get(0).path("note").asText()).isEqualTo("third");
        assertThat(list.get(2).path("note").asText()).isEqualTo("first");
        assertThat(list.get(0).path("versionNumber").asInt()).isEqualTo(3);
        for (JsonNode s : list) {
            assertUuid(s.path("id"));
            assertThat(s.path("versionNumber").isInt()).isTrue();
            assertThat(s.path("createdAt").isTextual()).isTrue();
        }
    }

    @Test
    void listVersionsViewShareTokenReturns200() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        createVersion(a, id, "v");
        String token = shareToken(a, id, "view");
        Res r = get(versions(id)).share(token).send();
        assertStatus(r, 200);
        assertThat(items(r.json())).hasSize(1);
    }

    @Test
    void listVersionsEditShareTokenReturns200() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        String token = shareToken(a, id, "edit");
        assertStatus(get(versions(id)).share(token).send(), 200);
    }

    @Test
    void listVersionsOtherUserOnPrivateDiagramReturns403() {
        Actor a = registerAndLogin();
        Actor b = registerAndLogin();
        String id = createDiagramId(a);
        createVersion(a, id, "secret");
        Res r = get(versions(id)).bearer(b.accessToken()).send();
        assertError(r, 403);
        assertThat(r.body()).doesNotContain("secret");
    }

    @Test
    void listVersionsOtherUserOnPublicDiagramReturns403() {
        // SPEC-AMBIGUOUS: spec table only says "Authenticated"; flow doc §2 says a public diagram does NOT
        // open its version history to outsiders (canViewHistory requires owner or a share link). We follow the flow doc.
        Actor a = registerAndLogin();
        Actor b = registerAndLogin();
        String id = createDiagramId(a);
        createVersion(a, id, "old secret");
        setPublic(a, id, true);
        Res r = get(versions(id)).bearer(b.accessToken()).send();
        assertError(r, 403);
        assertThat(r.body()).doesNotContain("old secret");
    }

    @Test
    void listVersionsAnonymousReturns401() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        assertError(get(versions(id)).send(), 401);
    }

    @Test
    void listVersionsAnonymousOnPublicDiagramReturns401() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        setPublic(a, id, true);
        assertError(get(versions(id)).send(), 401);
    }

    @Test
    void listVersionsUnknownDiagramReturns404() {
        Actor a = registerAndLogin();
        assertError(get(versions(UUID.randomUUID().toString())).bearer(a.accessToken()).send(), 404);
    }

    @Test
    void listVersionsShareTokenOfOtherDiagramIsDenied() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        String tokenOfOther = shareToken(a, createDiagramId(a), "edit");
        assertStatusIn(get(versions(id)).share(tokenOfOther).send(), 401, 403);
    }

    // ------------------------------------------------------------------ GET version detail

    @Test
    void getVersionOwnerReturns200WithData() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        setData(a, id, dataWith("D1"));
        JsonNode created = createVersion(a, id, "n");
        Res r = get(versions(id) + "/" + created.path("id").asText()).bearer(a.accessToken()).send();
        assertStatus(r, 200);
        assertVersionDetailShape(r.json());
        assertThat(r.json().path("data")).isEqualTo(dataWith("D1"));
        assertThat(r.json().path("note").asText()).isEqualTo("n");
    }

    @Test
    void getVersionViewShareTokenReturns200() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        JsonNode v = createVersion(a, id, "n");
        String token = shareToken(a, id, "view");
        assertStatus(get(versions(id) + "/" + v.path("id").asText()).share(token).send(), 200);
    }

    @Test
    void getVersionOtherUserReturns403() {
        Actor a = registerAndLogin();
        Actor b = registerAndLogin();
        String id = createDiagramId(a);
        JsonNode v = createVersion(a, id, "n");
        assertError(get(versions(id) + "/" + v.path("id").asText()).bearer(b.accessToken()).send(), 403);
    }

    @Test
    void getVersionAnonymousReturns401() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        JsonNode v = createVersion(a, id, "n");
        assertError(get(versions(id) + "/" + v.path("id").asText()).send(), 401);
    }

    @Test
    void getVersionUnknownVersionReturns404() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        assertError(get(versions(id) + "/" + UUID.randomUUID()).bearer(a.accessToken()).send(), 404);
    }

    @Test
    void getVersionBelongingToOtherDiagramReturns404() {
        Actor a = registerAndLogin();
        String d1 = createDiagramId(a);
        String d2 = createDiagramId(a);
        JsonNode v = createVersion(a, d1, "n");
        assertError(get(versions(d2) + "/" + v.path("id").asText()).bearer(a.accessToken()).send(), 404);
    }

    @Test
    void getVersionOfOtherUsersDiagramViaOwnDiagramPathReturns404() {
        // B must not read A's snapshot by pairing A's version id with B's own diagram id.
        Actor a = registerAndLogin();
        Actor b = registerAndLogin();
        String aDiagram = createDiagramId(a);
        setData(a, aDiagram, dataWith("A-SECRET"));
        JsonNode v = createVersion(a, aDiagram, "n");
        String bDiagram = createDiagramId(b);
        Res r = get(versions(bDiagram) + "/" + v.path("id").asText()).bearer(b.accessToken()).send();
        assertError(r, 404);
        assertThat(r.body()).doesNotContain("A-SECRET");
    }

    @Test
    void getVersionMalformedIdReturns400() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        assertError(get(versions(id) + "/xyz").bearer(a.accessToken()).send(), 400);
    }

    // ------------------------------------------------------------------ immutability

    @Test
    void versionSnapshotUnchangedAfterDiagramUpdate() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        setData(a, id, dataWith("ORIG"));
        JsonNode v = createVersion(a, id, "orig");
        setData(a, id, dataWith("CHANGED"));
        Res r = get(versions(id) + "/" + v.path("id").asText()).bearer(a.accessToken()).send();
        assertStatus(r, 200);
        assertThat(r.json().path("data")).isEqualTo(dataWith("ORIG"));
        assertThat(r.json().path("note").asText()).isEqualTo("orig");
        assertThat(r.json().path("versionNumber").asInt()).isEqualTo(v.path("versionNumber").asInt());
    }

    @Test
    void versionCannotBeModifiedOrDeletedViaHttp() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        setData(a, id, dataWith("IMM"));
        JsonNode v = createVersion(a, id, "imm");
        String url = versions(id) + "/" + v.path("id").asText();
        Res putRes = put(url).bearer(a.accessToken()).json(obj("note", "hacked")).send();
        Res patchRes = patch(url).bearer(a.accessToken()).json(obj("note", "hacked")).send();
        Res delRes = delete(url).bearer(a.accessToken()).send();
        // No mutation endpoints exist for versions: expect 405 Method Not Allowed.
        assertError(putRes, 405);
        assertError(patchRes, 405);
        assertError(delRes, 405);
        JsonNode after = get(url).bearer(a.accessToken()).send().json();
        assertThat(after.path("note").asText()).isEqualTo("imm");
        assertThat(after.path("data")).isEqualTo(dataWith("IMM"));
    }

    // ------------------------------------------------------------------ restore

    @Test
    void restoreOwnerReturns200BacksUpCurrentThenCopiesSnapshot() {
        Actor a = registerAndLogin();
        JsonNode d0 = createDiagram(a, "ERD", "Keep title");
        String id = d0.path("id").asText();
        setData(a, id, dataWith("OLD"));
        JsonNode v1 = createVersion(a, id, "old");
        JsonNode current = setData(a, id, dataWith("CURRENT"));
        int vBefore = current.path("version").asInt();

        Res r = post(versions(id) + "/" + v1.path("id").asText() + "/restore").bearer(a.accessToken()).send();
        assertStatus(r, 200);
        JsonNode body = r.json();
        assertThat(body.path("diagramId").asText()).isEqualTo(id);
        assertThat(body.path("data")).isEqualTo(dataWith("OLD"));
        assertThat(body.path("version").asInt()).isEqualTo(vBefore + 1);
        assertThat(body.path("title").asText()).isEqualTo("Keep title");
        assertThat(body.path("diagramType").asText()).isEqualTo("ERD");
        assertThat(body.path("updatedAt").isTextual()).isTrue();
        JsonNode backup = body.path("backupVersion");
        assertThat(backup.isObject()).as("backupVersion in %s", body).isTrue();
        assertUuid(backup.path("id"));
        assertThat(backup.path("versionNumber").asInt()).isEqualTo(2);

        // backup snapshot holds the pre-restore state
        Res bk = get(versions(id) + "/" + backup.path("id").asText()).bearer(a.accessToken()).send();
        assertStatus(bk, 200);
        assertThat(bk.json().path("data")).isEqualTo(dataWith("CURRENT"));

        // the diagram now has the restored data; metadata unchanged
        JsonNode after = getDiagram(a, id);
        assertThat(after.path("data")).isEqualTo(dataWith("OLD"));
        assertThat(after.path("version").asInt()).isEqualTo(vBefore + 1);
        assertThat(after.path("title").asText()).isEqualTo("Keep title");
        assertThat(after.path("isPublic").asBoolean()).isFalse();

        // the restored snapshot itself is untouched, and the list now has 2 entries, newest first
        JsonNode v1After = get(versions(id) + "/" + v1.path("id").asText()).bearer(a.accessToken()).send().json();
        assertThat(v1After.path("data")).isEqualTo(dataWith("OLD"));
        assertThat(v1After.path("note").asText()).isEqualTo("old");
        List<JsonNode> list = items(get(versions(id)).bearer(a.accessToken()).send().json());
        assertThat(list).hasSize(2);
        assertThat(list.get(0).path("id").asText()).isEqualTo(backup.path("id").asText());
    }

    @Test
    void restoreBackupNoteMentionsRestoredVersion() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        JsonNode v1 = createVersion(a, id, "first");
        Res r = post(versions(id) + "/" + v1.path("id").asText() + "/restore").bearer(a.accessToken()).send();
        assertStatus(r, 200);
        // flow doc §6: note = "Auto backup before restoring version N"
        assertThat(r.json().path("backupVersion").path("note").asText()).containsIgnoringCase("backup");
    }

    @Test
    void restoreThenOldVersionCanBeUsedToUndoRestore() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        setData(a, id, dataWith("A"));
        JsonNode vA = createVersion(a, id, "A");
        setData(a, id, dataWith("B"));
        JsonNode restore = post(versions(id) + "/" + vA.path("id").asText() + "/restore").bearer(a.accessToken()).send().json();
        String backupId = restore.path("backupVersion").path("id").asText();
        Res undo = post(versions(id) + "/" + backupId + "/restore").bearer(a.accessToken()).send();
        assertStatus(undo, 200);
        assertThat(getDiagram(a, id).path("data")).isEqualTo(dataWith("B"));
    }

    @Test
    void restoreAfterRestoreIncrementsDiagramVersionAndOptimisticLockStillApplies() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        JsonNode v1 = createVersion(a, id, "v1");
        JsonNode before = getDiagram(a, id);
        assertStatus(post(versions(id) + "/" + v1.path("id").asText() + "/restore").bearer(a.accessToken()).send(), 200);
        // a client still holding the pre-restore version must now get 409
        Res stale = updateDiagram(a, id, before.path("title").asText(), "UML", before.path("version").asInt(), dataWith("Z"), false);
        assertError(stale, 409);
    }

    @Test
    void restoreEditShareTokenReturns200() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        setData(a, id, dataWith("OLD"));
        JsonNode v = createVersion(a, id, "old");
        setData(a, id, dataWith("NEW"));
        String token = shareToken(a, id, "edit");
        Res r = post(versions(id) + "/" + v.path("id").asText() + "/restore").share(token).send();
        assertStatus(r, 200);
        assertThat(getDiagram(a, id).path("data")).isEqualTo(dataWith("OLD"));
    }

    @Test
    void restoreViewShareTokenReturns403AndChangesNothing() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        setData(a, id, dataWith("OLD"));
        JsonNode v = createVersion(a, id, "old");
        setData(a, id, dataWith("NEW"));
        String token = shareToken(a, id, "view");
        assertError(post(versions(id) + "/" + v.path("id").asText() + "/restore").share(token).send(), 403);
        assertThat(getDiagram(a, id).path("data")).isEqualTo(dataWith("NEW"));
        assertThat(items(get(versions(id)).bearer(a.accessToken()).send().json())).hasSize(1);
    }

    @Test
    void restoreOtherUserReturns403AndChangesNothing() {
        Actor a = registerAndLogin();
        Actor b = registerAndLogin();
        String id = createDiagramId(a);
        setData(a, id, dataWith("OLD"));
        JsonNode v = createVersion(a, id, "old");
        setData(a, id, dataWith("NEW"));
        assertError(post(versions(id) + "/" + v.path("id").asText() + "/restore").bearer(b.accessToken()).send(), 403);
        assertThat(getDiagram(a, id).path("data")).isEqualTo(dataWith("NEW"));
        assertThat(items(get(versions(id)).bearer(a.accessToken()).send().json())).hasSize(1);
    }

    @Test
    void restoreAnonymousReturns401() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        JsonNode v = createVersion(a, id, "old");
        assertError(post(versions(id) + "/" + v.path("id").asText() + "/restore").send(), 401);
    }

    @Test
    void restoreUnknownVersionReturns404AndCreatesNoBackup() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        assertError(post(versions(id) + "/" + UUID.randomUUID() + "/restore").bearer(a.accessToken()).send(), 404);
        assertThat(items(get(versions(id)).bearer(a.accessToken()).send().json())).isEmpty();
    }

    @Test
    void restoreVersionOfOtherDiagramReturns404AndChangesNothing() {
        Actor a = registerAndLogin();
        String d1 = createDiagramId(a);
        String d2 = createDiagramId(a);
        setData(a, d1, dataWith("D1"));
        JsonNode v = createVersion(a, d1, "d1");
        setData(a, d2, dataWith("D2"));
        assertError(post(versions(d2) + "/" + v.path("id").asText() + "/restore").bearer(a.accessToken()).send(), 404);
        assertThat(getDiagram(a, d2).path("data")).isEqualTo(dataWith("D2"));
        assertThat(items(get(versions(d2)).bearer(a.accessToken()).send().json())).isEmpty();
    }

    @Test
    void restoreUnknownDiagramReturns404() {
        Actor a = registerAndLogin();
        assertError(post(versions(UUID.randomUUID().toString()) + "/" + UUID.randomUUID() + "/restore")
                .bearer(a.accessToken()).send(), 404);
    }
}
