package com.diagramio.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

class CrossCuttingE2eTest extends E2eTestBase {

    @Test
    void unauthenticatedErrorUsesUniformErrorBody() {
        Res r = get(API + "/users/me").send();
        assertError(r, 401);
        assertThat(r.json().path("path").asText()).isEqualTo(API + "/users/me");
        assertThat(r.json().path("error").asText()).isEqualTo("Unauthorized");
    }

    @Test
    void forbiddenErrorUsesUniformErrorBody() {
        Actor a = registerAndLogin();
        Actor b = registerAndLogin();
        String id = createDiagramId(a);
        Res r = get(API + "/diagrams/" + id).bearer(b.accessToken()).send();
        assertError(r, 403);
        assertThat(r.json().path("error").asText()).isEqualTo("Forbidden");
        assertThat(r.json().path("path").asText()).isEqualTo(API + "/diagrams/" + id);
    }

    @Test
    void validationErrorUsesUniformErrorBodyWithMessage() {
        Actor a = registerAndLogin();
        Res r = post(API + "/diagrams").bearer(a.accessToken()).json(obj("title", "x", "diagramType", "NOPE")).send();
        assertError(r, 400);
        assertThat(r.json().path("error").asText()).isEqualTo("Bad Request");
        assertThat(r.json().path("message").asText()).isNotBlank();
    }

    @Test
    void conflictErrorUsesUniformErrorBody() {
        Actor a = registerAndLogin();
        Res r = register("u" + uniq(), a.email(), PASSWORD);
        assertError(r, 409);
        assertThat(r.json().path("error").asText()).isEqualTo("Conflict");
    }

    @Test
    void unknownApiPathAuthenticatedReturns404WithUniformBody() {
        Actor a = registerAndLogin();
        Res r = get(API + "/does-not-exist").bearer(a.accessToken()).send();
        assertError(r, 404);
    }

    @Test
    void unknownApiPathAnonymousIsNotFoundOrUnauthorized() {
        // SPEC-AMBIGUOUS: an unknown path without credentials may legitimately be 401 (secured by default) or 404.
        Res r = get(API + "/does-not-exist").send();
        assertStatusIn(r, 401, 404);
        assertErrorBody(r);
    }

    @Test
    void wrongMethodOnExistingPathReturns405WithUniformBodyAndAllowHeader() {
        Actor a = registerAndLogin();
        Res r = delete(API + "/users/me").bearer(a.accessToken()).send();
        assertError(r, 405);
        // RFC 9110: a 405 response MUST include Allow
        assertThat(r.header("Allow")).as("Allow header").isNotNull().contains("GET");
    }

    @Test
    void wrongMethodOnDiagramItemReturns405WithAllowHeader() {
        Actor a = registerAndLogin();
        String id = createDiagramId(a);
        Res r = patch(API + "/diagrams/" + id).bearer(a.accessToken()).json(obj("title", "x")).send();
        assertError(r, 405);
        assertThat(r.header("Allow")).isNotNull().contains("GET").contains("PUT").contains("DELETE");
    }

    @Test
    void wrongMethodOnDiagramsCollectionReturns405() {
        Actor a = registerAndLogin();
        assertError(delete(API + "/diagrams").bearer(a.accessToken()).send(), 405);
    }

    @Test
    void malformedJsonReturns400WithUniformBody() {
        Actor a = registerAndLogin();
        Res r = post(API + "/diagrams").bearer(a.accessToken()).rawJson("{not json").send();
        assertError(r, 400);
        // must not leak parser internals / stack traces
        assertThat(r.body()).doesNotContain("com.fasterxml").doesNotContain("at com.");
    }

    @Test
    void emptyBodyOnJsonEndpointReturns400() {
        Actor a = registerAndLogin();
        Res r = post(API + "/diagrams").bearer(a.accessToken()).header("Content-Type", "application/json").send();
        assertError(r, 400);
    }

    @Test
    void unsupportedContentTypeIsRejectedWithUniformBody() {
        Actor a = registerAndLogin();
        Res r = post(API + "/diagrams").bearer(a.accessToken()).header("Content-Type", "text/plain")
                .rawJson("{\"title\":\"x\",\"diagramType\":\"UML\"}").send();
        assertError(r, 415);
    }

    @Test
    void malformedUuidPathReturns400WithUniformBody() {
        Actor a = registerAndLogin();
        Res r = get(API + "/diagrams/12345/versions").bearer(a.accessToken()).send();
        assertError(r, 400);
    }

    @Test
    void responsesDoNotCreateHttpSession() {
        Actor a = registerAndLogin();
        Res login = login(a.email(), PASSWORD);
        Res me = get(API + "/users/me").bearer(a.accessToken()).send();
        Res denied = get(API + "/users/me").send();
        for (Res r : new Res[] {login, me, denied}) {
            assertThat(r.headers("Set-Cookie")).as("stateless API must not set cookies").noneMatch(c -> c.startsWith("JSESSIONID"));
        }
    }

    @Test
    void jsonResponsesHaveJsonContentType() {
        Actor a = registerAndLogin();
        Res ok = get(API + "/users/me").bearer(a.accessToken()).send();
        Res err = get(API + "/users/me").send();
        assertThat(ok.header("Content-Type")).contains("application/json");
        assertThat(err.header("Content-Type")).contains("application/json");
    }

    @Test
    void serverErrorsDoNotLeakStackTraces() {
        Actor a = registerAndLogin();
        Res r = post(API + "/diagrams").bearer(a.accessToken()).json(obj("title", "t".repeat(10_000), "diagramType", "UML")).send();
        assertThat(r.status()).as("oversized title must be a client error, body=%s", r.body()).isLessThan(500);
        assertThat(r.body()).doesNotContain("Exception").doesNotContain("org.hibernate").doesNotContain("SQL");
    }

    @Test
    void openApiDocsAreServed() {
        // Spec: OpenAPI/Swagger is in scope.
        Res r = get("/v3/api-docs").send();
        assertStatus(r, 200);
        assertThat(r.json().path("paths").has(API + "/diagrams")).isTrue();
    }

    @Test
    void openApiDocsDeclareDataAsObjectInAllDiagramPayloads() {
        JsonNode schemas = get("/v3/api-docs").send().json().path("components").path("schemas");
        for (String name : new String[] {"DiagramResponse", "DiagramVersionResponse", "RestoreVersionResponse",
                "SharedDiagramResponse", "UpdateDiagramRequest"}) {
            JsonNode data = schemas.path(name).path("properties").path("data");
            assertThat(data.isMissingNode()).as("%s.data present", name).isFalse();
            assertThat(data.path("type").asText()).as("%s.data type", name).isEqualTo("object");
        }
    }

    @Test
    void openApiDocsConstrainTitleLength() {
        JsonNode schemas = get("/v3/api-docs").send().json().path("components").path("schemas");
        for (String name : new String[] {"CreateDiagramRequest", "UpdateDiagramRequest"}) {
            JsonNode title = schemas.path(name).path("properties").path("title");
            assertThat(title.path("maxLength").asInt()).as("%s.title maxLength", name).isEqualTo(255);
            assertThat(title.path("minLength").asInt()).as("%s.title minLength", name).isEqualTo(1);
        }
    }

    @Test
    void openApiDocsListDocumentedResponseCodes() {
        JsonNode paths = get("/v3/api-docs").send().json().path("paths");
        assertResponses(paths, API + "/auth/register", "post", "201", "400", "409");
        assertResponses(paths, API + "/auth/login", "post", "200", "400", "401");
        assertResponses(paths, API + "/auth/refresh-token", "post", "200", "400", "401");
        assertResponses(paths, API + "/auth/logout", "post", "204", "400", "401");
        assertResponses(paths, API + "/users/me", "get", "200", "401");
        assertResponses(paths, API + "/users/me/password", "patch", "204", "400", "401");
        assertResponses(paths, API + "/shares/{token}", "get", "200", "404");
        assertResponses(paths, API + "/diagrams/{id}/shares", "get", "200", "401", "403", "404");
        assertResponses(paths, API + "/diagrams/{id}/shares/{share_id}", "delete", "204", "401", "403", "404");
        JsonNode listShares200 = paths.path(API + "/diagrams/{id}/shares").path("get").path("responses").path("200");
        assertThat(listShares200.toString()).contains("array").contains("ShareResponse");
    }

    private static void assertResponses(JsonNode paths, String path, String method, String... codes) {
        JsonNode responses = paths.path(path).path(method).path("responses");
        assertThat(responses.isObject()).as("%s %s documented", method, path).isTrue();
        for (String c : codes) {
            assertThat(responses.has(c)).as("%s %s documents %s; has %s", method, path, c, responses).isTrue();
        }
    }

    @Test
    void unsupportedContentTypeOnPutReturns415AndLeavesDiagramUnchanged() {
        Actor a = registerAndLogin();
        JsonNode d = createDiagram(a, "UML");
        String id = d.path("id").asText();
        Res r = put(API + "/diagrams/" + id).bearer(a.accessToken()).header("Content-Type", "application/xml")
                .rawJson("<diagram/>").send();
        assertError(r, 415);
        assertThat(getDiagram(a, id).path("version").asInt()).isEqualTo(d.path("version").asInt());
    }

    @Test
    void wrongTypedQueryParamReturns400WithUniformBody() {
        Actor a = registerAndLogin();
        assertError(get(API + "/diagrams?size=lots").bearer(a.accessToken()).send(), 400);
    }

    @Test
    void swaggerUiIsServed() {
        Res r = get("/swagger-ui/index.html").header("Accept", "text/html").send();
        assertStatus(r, 200);
    }
}
