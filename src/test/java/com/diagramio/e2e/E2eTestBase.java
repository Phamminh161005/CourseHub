package com.diagramio.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Black-box E2E base: boots the whole app on a random port against a real PostgreSQL
 * (Testcontainers) and talks to it ONLY over HTTP.
 *
 * <p>The container is a JVM-wide singleton started in a static block (not {@code @Container}),
 * so every E2E class shares one container and one cached Spring context. If Docker is not
 * available the static block throws and the suite fails loudly instead of being skipped.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class E2eTestBase {

    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    static {
        postgres.start();
    }

    protected static final String API = "/api/v1";
    protected static final String PASSWORD = "Passw0rd!123";
    protected static final ObjectMapper JSON = new ObjectMapper();

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @LocalServerPort
    protected int port;

    // ------------------------------------------------------------------ HTTP layer

    /** Response wrapper: status, headers, raw body, lazily parsed JSON. */
    public record Res(int status, HttpResponse<String> raw) {
        public String body() {
            return raw.body();
        }

        public JsonNode json() {
            try {
                String b = raw.body();
                return (b == null || b.isBlank()) ? JSON.missingNode() : JSON.readTree(b);
            } catch (Exception e) {
                throw new AssertionError("Response body is not JSON (status " + status + "): " + raw.body(), e);
            }
        }

        public String header(String name) {
            return raw.headers().firstValue(name).orElse(null);
        }

        public List<String> headers(String name) {
            return raw.headers().allValues(name);
        }

        @Override
        public String toString() {
            return status + " " + raw.body();
        }
    }

    /** Fluent request builder. */
    public final class Req {
        private final String method;
        private final String path;
        private final Map<String, String> headers = new LinkedHashMap<>();
        private String body;

        Req(String method, String path) {
            this.method = method;
            this.path = path;
            headers.put("Accept", "application/json");
        }

        public Req bearer(String accessToken) {
            if (accessToken != null) {
                headers.put("Authorization", "Bearer " + accessToken);
            }
            return this;
        }

        public Req share(String shareToken) {
            if (shareToken != null) {
                headers.put("X-Share-Token", shareToken);
            }
            return this;
        }

        public Req as(Actor actor) {
            if (actor != null) {
                bearer(actor.accessToken());
                share(actor.shareToken());
            }
            return this;
        }

        public Req header(String name, String value) {
            headers.put(name, value);
            return this;
        }

        /** Raw body string, sent as application/json unless a Content-Type was set. */
        public Req rawJson(String json) {
            this.body = json;
            headers.putIfAbsent("Content-Type", "application/json");
            return this;
        }

        public Req json(Object payload) {
            try {
                return rawJson(payload instanceof String s ? s : JSON.writeValueAsString(payload));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        public Res send() {
            String url = path.startsWith("http") ? path : "http://localhost:" + port + path;
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(60));
            headers.forEach(b::header);
            b.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body));
            try {
                HttpResponse<String> r = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
                return new Res(r.statusCode(), r);
            } catch (Exception e) {
                throw new RuntimeException(method + " " + url + " failed", e);
            }
        }
    }

    protected Req get(String path) {
        return new Req("GET", path);
    }

    protected Req post(String path) {
        return new Req("POST", path);
    }

    protected Req put(String path) {
        return new Req("PUT", path);
    }

    protected Req patch(String path) {
        return new Req("PATCH", path);
    }

    protected Req delete(String path) {
        return new Req("DELETE", path);
    }

    protected Req method(String method, String path) {
        return new Req(method, path);
    }

    // ------------------------------------------------------------------ JSON helpers

    protected static ObjectNode obj(Object... kv) {
        ObjectNode n = JSON.createObjectNode();
        for (int i = 0; i < kv.length; i += 2) {
            n.set((String) kv[i], JSON.valueToTree(kv[i + 1]));
        }
        return n;
    }

    protected static JsonNode parse(String json) {
        try {
            return JSON.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected static JsonNode emptyData() {
        return parse("{\"nodes\":[],\"edges\":[]}");
    }

    /** A valid diagram data payload with a recognisable marker node. */
    protected static JsonNode dataWith(String marker) {
        return parse("{\"nodes\":[{\"id\":\"" + marker + "\",\"label\":\"" + marker
                + "\"}],\"edges\":[]}");
    }

    protected static String uniq() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    // ------------------------------------------------------------------ assertions

    /** Asserts the uniform error body {timestamp,status,error,message,path} and the status. */
    protected static void assertError(Res res, int expectedStatus) {
        assertThat(res.status()).as("status, body=%s", res.body()).isEqualTo(expectedStatus);
        assertErrorBody(res);
    }

    protected static void assertErrorBody(Res res) {
        JsonNode j = res.json();
        assertThat(j.isObject()).as("error body must be a JSON object: %s", res.body()).isTrue();
        assertThat(j.path("timestamp").isTextual()).as("timestamp in %s", res.body()).isTrue();
        assertThat(j.path("status").isInt()).as("status in %s", res.body()).isTrue();
        assertThat(j.path("status").asInt()).as("status field matches HTTP status").isEqualTo(res.status());
        assertThat(j.path("error").isTextual()).as("error in %s", res.body()).isTrue();
        assertThat(j.has("message")).as("message in %s", res.body()).isTrue();
        assertThat(j.path("path").isTextual()).as("path in %s", res.body()).isTrue();
        assertThat(j.path("path").asText()).startsWith(API);
    }

    protected static void assertStatusIn(Res res, Integer... allowed) {
        assertThat(res.status()).as("status, body=%s", res.body()).isIn((Object[]) allowed);
    }

    protected static void assertStatus(Res res, int expected) {
        assertThat(res.status()).as("status, body=%s", res.body()).isEqualTo(expected);
    }

    protected static void assertUuid(JsonNode n) {
        assertThat(n.isTextual()).as("expected uuid string but was %s", n).isTrue();
        UUID.fromString(n.asText());
    }

    // ------------------------------------------------------------------ fixtures

    /** Logged-in user and/or share-token holder. */
    public record Actor(String userId, String email, String username, String accessToken,
                        String refreshToken, String shareToken) {
        public static Actor anonymous() {
            return new Actor(null, null, null, null, null, null);
        }

        public static Actor shareOnly(String shareToken) {
            return new Actor(null, null, null, null, null, shareToken);
        }

        public Actor withShare(String token) {
            return new Actor(userId, email, username, accessToken, refreshToken, token);
        }
    }

    protected Res register(String username, String email, String password) {
        return post(API + "/auth/register").json(obj("username", username, "email", email, "password", password)).send();
    }

    protected Res login(String email, String password) {
        return post(API + "/auth/login").json(obj("email", email, "password", password)).send();
    }

    /** Registers a fresh random user and logs in. */
    protected Actor registerAndLogin() {
        String u = "u" + uniq();
        String email = u + "@e2e.test";
        Res r = register(u, email, PASSWORD);
        assertThat(r.status()).as("register fixture: %s", r.body()).isEqualTo(201);
        Res l = login(email, PASSWORD);
        assertThat(l.status()).as("login fixture: %s", l.body()).isEqualTo(200);
        return new Actor(r.json().path("id").asText(), email, u,
                l.json().path("accessToken").asText(), l.json().path("refreshToken").asText(), null);
    }

    protected JsonNode createDiagram(Actor owner, String type) {
        return createDiagram(owner, type, "Diagram " + uniq());
    }

    protected JsonNode createDiagram(Actor owner, String type, String title) {
        Res r = post(API + "/diagrams").bearer(owner.accessToken())
                .json(obj("title", title, "diagramType", type)).send();
        assertThat(r.status()).as("create diagram fixture: %s", r.body()).isEqualTo(201);
        return r.json();
    }

    protected String createDiagramId(Actor owner) {
        return createDiagram(owner, "UML").path("id").asText();
    }

    protected JsonNode getDiagram(Actor owner, String id) {
        Res r = get(API + "/diagrams/" + id).as(owner).send();
        assertThat(r.status()).as("get diagram fixture: %s", r.body()).isEqualTo(200);
        return r.json();
    }

    /** PUT with full body built from the current state. */
    protected Res updateDiagram(Actor actor, String id, String title, String type, int version,
                                JsonNode data, Boolean isPublic) {
        ObjectNode body = obj("title", title, "diagramType", type, "version", version);
        body.set("data", data);
        if (isPublic != null) {
            body.put("isPublic", isPublic);
        }
        return put(API + "/diagrams/" + id).as(actor).json(body).send();
    }

    /** Owner sets data (and keeps metadata); returns new state. */
    protected JsonNode setData(Actor owner, String id, JsonNode data) {
        JsonNode cur = getDiagram(owner, id);
        Res r = updateDiagram(owner, id, cur.path("title").asText(), cur.path("diagramType").asText(),
                cur.path("version").asInt(), data, cur.path("isPublic").asBoolean());
        assertThat(r.status()).as("update fixture: %s", r.body()).isEqualTo(200);
        return r.json();
    }

    protected JsonNode setPublic(Actor owner, String id, boolean isPublic) {
        JsonNode cur = getDiagram(owner, id);
        Res r = updateDiagram(owner, id, cur.path("title").asText(), cur.path("diagramType").asText(),
                cur.path("version").asInt(), cur.path("data"), isPublic);
        assertThat(r.status()).as("set public fixture: %s", r.body()).isEqualTo(200);
        assertThat(r.json().path("isPublic").asBoolean()).isEqualTo(isPublic);
        return r.json();
    }

    protected JsonNode createShare(Actor owner, String diagramId, String role) {
        Res r = post(API + "/diagrams/" + diagramId + "/shares").bearer(owner.accessToken())
                .json(obj("role", role)).send();
        assertThat(r.status()).as("create share fixture: %s", r.body()).isEqualTo(201);
        return r.json();
    }

    protected String shareToken(Actor owner, String diagramId, String role) {
        return createShare(owner, diagramId, role).path("shareToken").asText();
    }

    protected JsonNode createVersion(Actor actor, String diagramId, String note) {
        Res r = post(API + "/diagrams/" + diagramId + "/versions").as(actor).json(obj("note", note)).send();
        assertThat(r.status()).as("create version fixture: %s", r.body()).isEqualTo(201);
        return r.json();
    }

    /** Walks all pages of a paginated endpoint and collects the content items. */
    protected List<JsonNode> allPages(Actor actor, String pathWithQuery) {
        List<JsonNode> items = new ArrayList<>();
        String sep = pathWithQuery.contains("?") ? "&" : "?";
        for (int page = 0; page < 1000; page++) {
            Res r = get(pathWithQuery + sep + "page=" + page + "&size=100").as(actor).send();
            assertThat(r.status()).as("paging %s: %s", pathWithQuery, r.body()).isEqualTo(200);
            JsonNode content = r.json().path("content");
            content.forEach(items::add);
            if (content.size() < 100) {
                break;
            }
        }
        return items;
    }

    protected static boolean containsId(List<JsonNode> items, String id) {
        return items.stream().anyMatch(n -> id.equals(n.path("id").asText()));
    }

    protected static List<JsonNode> filter(List<JsonNode> items, Predicate<JsonNode> p) {
        return items.stream().filter(p).toList();
    }

    /** Lists items of a response that is a bare JSON array or a page with "content". */
    protected static List<JsonNode> items(JsonNode arrayOrPage) {
        JsonNode arr = arrayOrPage.isArray() ? arrayOrPage : arrayOrPage.path("content");
        List<JsonNode> out = new ArrayList<>();
        arr.forEach(out::add);
        return out;
    }
}
