package com.diagramio.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.diagramio.domain.exception.ConflictException;
import com.diagramio.domain.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    @RestController
    static class ThrowingController {
        @GetMapping("/not-found")
        void notFound() {
            throw new NotFoundException("Diagram not found");
        }

        @GetMapping("/conflict")
        void conflict() {
            throw new ConflictException("Stale version");
        }

        @GetMapping("/duplicate")
        void duplicate() {
            throw new DataIntegrityViolationException("duplicate key value violates unique constraint \"users_email_key\"");
        }

        @GetMapping("/needs-param")
        void needsParam(@RequestParam String q) {
        }

        @PostMapping(value = "/json-only", consumes = MediaType.APPLICATION_JSON_VALUE)
        void jsonOnly(@RequestBody String body) {
        }
    }

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void notFoundExceptionBecomes404() throws Exception {
        mockMvc.perform(get("/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Diagram not found"))
                .andExpect(jsonPath("$.path").value("/not-found"));
    }

    @Test
    void conflictExceptionBecomes409() throws Exception {
        mockMvc.perform(get("/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void dataIntegrityViolationBecomes409WithoutLeakingSql() throws Exception {
        mockMvc.perform(get("/duplicate"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Request conflicts with existing data, please retry"));
    }

    @Test
    void wrongHttpMethodBecomes405WithAllowHeader() throws Exception {
        mockMvc.perform(put("/not-found"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "GET"))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.path").value("/not-found"));
    }

    @Test
    void missingRequestParameterBecomes400() throws Exception {
        mockMvc.perform(get("/needs-param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void unsupportedContentTypeBecomes415() throws Exception {
        mockMvc.perform(post("/json-only").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));
    }
}
