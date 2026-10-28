package com.diagramio.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

class HealthE2eTest extends E2eTestBase {

    @Test
    void healthAnonymousReturns200WithStringMap() {
        Res r = get(API + "/health").send();
        assertStatus(r, 200);
        JsonNode j = r.json();
        assertThat(j.isObject()).isTrue();
        assertThat(j.size()).isPositive();
        // openapi: object with additionalProperties of type string
        j.fields().forEachRemaining(e ->
                assertThat(e.getValue().isTextual()).as("health field %s must be a string", e.getKey()).isTrue());
    }

    @Test
    void healthWithInvalidBearerStillReturns200() {
        // Public endpoint: a junk token must not turn it into 401.
        // SPEC-AMBIGUOUS: spec only says "Public"; we read that as "credentials are ignored if invalid".
        Res r = get(API + "/health").bearer("not-a-jwt").send();
        assertStatus(r, 200);
    }

    @Test
    void healthReportsDatabaseUp() {
        Res r = get(API + "/health").send();
        assertStatus(r, 200);
        // SPEC-AMBIGUOUS: field names are not specified; with a live DB we expect some value to say "UP"/"OK".
        String body = r.body().toUpperCase();
        assertThat(body).containsAnyOf("UP", "OK", "HEALTHY", "CONNECTED");
    }
}
