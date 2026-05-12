package com.upi.mesh;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiSecurityTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    private static String adminToken;

    // ── Unauthenticated access ───────────────────────────────

    @Test @Order(1)
    void serverKeyIsPublic() throws Exception {
        mvc.perform(get("/api/server-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicKey").exists())
                .andExpect(jsonPath("$.algorithm").value("RSA-2048/OAEP-SHA256"));
    }

    @Test @Order(2)
    void statsRequiresAuth() throws Exception {
        mvc.perform(get("/api/stats"))
                .andExpect(status().isUnauthorized());
    }

    @Test @Order(3)
    void bridgeIngestRequiresAuth() throws Exception {
        mvc.perform(post("/api/bridge/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // ── Login ────────────────────────────────────────────────

    @Test @Order(4)
    void loginWithBadCredentialsFails() throws Exception {
        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("username", "admin", "password", "wrong"))))
                .andExpect(status().isUnauthorized());
    }

    @Test @Order(5)
    void loginSucceedsAndReturnsToken() throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("username", "admin", "password", "admin123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.role").value("ROLE_ADMIN"))
                .andReturn();

        Map<?, ?> body = mapper.readValue(result.getResponse().getContentAsString(), Map.class);
        adminToken = (String) body.get("token");
    }

    // ── Authenticated access ──────────────────────────────────

    @Test @Order(6)
    void statsAccessibleWithToken() throws Exception {
        mvc.perform(get("/api/stats")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTransactions").exists());
    }

    @Test @Order(7)
    void healthEndpointAlwaysPublic() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test @Order(8)
    void accountsListWithToken() throws Exception {
        mvc.perform(get("/api/accounts")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(4))));
    }
}
