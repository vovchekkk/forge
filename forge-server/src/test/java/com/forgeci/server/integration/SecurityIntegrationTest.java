package com.forgeci.server.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgeci.server.application.PipelineRunService;
import com.forgeci.server.application.PipelineService;
import com.forgeci.server.application.ProjectService;
import com.forgeci.server.application.RunnerService;
import com.forgeci.server.entity.PipelineEntity;
import com.forgeci.server.entity.PipelineRunEntity;
import com.forgeci.server.entity.ProjectEntity;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Security and multi-tenancy tests: authentication, refresh rotation, login
 * throttling, IDOR protection across users, and runner credential lifecycle.
 */
@AutoConfigureMockMvc
class SecurityIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ProjectService projectService;
    @Autowired
    private PipelineService pipelineService;
    @Autowired
    private PipelineRunService runService;
    @Autowired
    private RunnerService runnerService;

    record AuthTokens(UUID userId, String email, String accessToken, String refreshToken) {}

    private AuthTokens register(String email) throws Exception {
        return register(email, "test-password-123");
    }

    private AuthTokens register(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isCreated())
                .andReturn();
        return parseAuth(result);
    }

    private AuthTokens login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andReturn();
        return parseAuth(result);
    }

    private AuthTokens parseAuth(MvcResult result) throws Exception {
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        return new AuthTokens(
                UUID.fromString(node.get("userId").asText()),
                node.get("email").asText(),
                node.get("accessToken").asText(),
                node.get("refreshToken").asText());
    }

    private AuthTokens refresh(String refreshToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
                .andReturn();
        return parseAuth(result);
    }

    private UUID createProject(AuthTokens user, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + user.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", name,
                                "repositoryUrl", "https://example.com/" + name + ".git",
                                "repositoryBranch", "main"))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("id").asText());
    }

    @Test
    void registerLoginAndRefreshFlow() throws Exception {
        AuthTokens alice = register("alice@example.com");

        // Login returns working tokens
        AuthTokens loggedIn = login("alice@example.com", "test-password-123");
        assertTrue(loggedIn.accessToken() != null && !loggedIn.accessToken().isBlank());
        assertTrue(loggedIn.refreshToken() != null && !loggedIn.refreshToken().isBlank());

        // Protected endpoint accessible with the bearer token
        mockMvc.perform(get("/api/projects").header("Authorization", "Bearer " + loggedIn.accessToken()))
                .andExpect(status().isOk());

        // Refresh rotates the token; the old one must now be rejected
        AuthTokens refreshed = refresh(loggedIn.refreshToken());
        assertTrue(refreshed.accessToken() != null && !refreshed.accessToken().isBlank());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", loggedIn.refreshToken()))))
                .andExpect(status().isUnauthorized());

        // New refresh token still works
        AuthTokens second = refresh(refreshed.refreshToken());
        assertTrue(second.accessToken() != null && !second.accessToken().isBlank());
    }

    @Test
    void protectedEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/projects")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/projects")
                        .header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/projects/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginRejectsBadPasswordAndThrottles() throws Exception {
        AuthTokens alice = register("throttled@example.com");

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "email", "throttled@example.com", "password", "wrong-password"))))
                    .andExpect(status().isUnauthorized());
        }
        // 6th attempt within the window is throttled
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "throttled@example.com", "password", "test-password-123"))))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void projectAccessIsScopedToOwner() throws Exception {
        AuthTokens alice = register("alice2@example.com");
        AuthTokens bob = register("bob2@example.com");

        UUID projectId = createProject(alice, "alice-repo");

        // Alice can read her own project
        mockMvc.perform(get("/api/projects/" + projectId)
                        .header("Authorization", "Bearer " + alice.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("alice-repo"));

        // Bob cannot read, delete, or list Alice's project
        mockMvc.perform(get("/api/projects/" + projectId)
                        .header("Authorization", "Bearer " + bob.accessToken()))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/projects/" + projectId)
                        .header("Authorization", "Bearer " + bob.accessToken()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/projects").header("Authorization", "Bearer " + bob.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void pipelineAndRunAccessIsScopedToOwner() throws Exception {
        AuthTokens alice = register("alice3@example.com");
        AuthTokens bob = register("bob3@example.com");

        UUID projectId = createProject(alice, "alice-pipe-repo");
        String config = """
                name: CI
                image: alpine
                jobs:
                  build: { commands: [echo build] }
                """;

        UUID pipelineId = UUID.fromString(objectMapper.readTree(mockMvc.perform(post("/api/projects/" + projectId + "/pipelines")
                        .header("Authorization", "Bearer " + alice.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("config", config))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).get("id").asText());

        UUID runId = UUID.fromString(objectMapper.readTree(mockMvc.perform(post("/api/pipelines/" + pipelineId + "/runs")
                        .header("Authorization", "Bearer " + alice.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("branch", "main"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).get("id").asText());

        // Bob cannot see Alice's pipeline or run
        mockMvc.perform(get("/api/pipelines/" + pipelineId)
                        .header("Authorization", "Bearer " + bob.accessToken()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/pipeline-runs/" + runId)
                        .header("Authorization", "Bearer " + bob.accessToken()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/pipeline-runs/" + runId + "/cancel")
                        .header("Authorization", "Bearer " + bob.accessToken()))
                .andExpect(status().isNotFound());

        // Alice can cancel her own run
        mockMvc.perform(post("/api/pipeline-runs/" + runId + "/cancel")
                        .header("Authorization", "Bearer " + alice.accessToken()))
                .andExpect(status().isOk());
    }

    @Test
    void runnerCannotClaimJobsOfAnotherOwner() throws Exception {
        UUID aliceId = createUser("alice-runner@example.com");
        UUID bobId = createUser("bob-runner@example.com");

        ProjectEntity aliceProject = projectService.create(aliceId, "alice-repo",
                "https://example.com/alice-repo.git", "main");
        PipelineEntity alicePipeline = pipelineService.create(aliceId, aliceProject.getId(), """
                name: Alice CI
                image: alpine
                jobs:
                  build: { commands: [echo build] }
                """);
        PipelineRunEntity aliceRun = runService.start(aliceId, alicePipeline.getId(), "main");

        UUID aliceRunner = createRunner(aliceId, "alice-runner");
        UUID bobRunner = createRunner(bobId, "bob-runner");

        // Alice's runner claims Alice's job
        assertTrue(runnerService.claimNextJob(aliceRunner).isPresent());
        // Bob's runner sees nothing
        assertTrue(runnerService.claimNextJob(bobRunner).isEmpty());
    }

    @Test
    void runnerCredentialLifecycle() throws Exception {
        AuthTokens alice = register("runner-owner@example.com");
        AuthTokens bob = register("runner-bob@example.com");

        // Alice creates a runner credential; token returned exactly once
        MvcResult create = mockMvc.perform(post("/api/runners")
                        .header("Authorization", "Bearer " + alice.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "ci-runner"))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode created = objectMapper.readTree(create.getResponse().getContentAsString(StandardCharsets.UTF_8));
        UUID runnerId = UUID.fromString(created.get("runnerId").asText());
        String token = created.get("registrationToken").asText();
        assertTrue(token != null && !token.isBlank());

        // Register with the credential (public, idempotent)
        mockMvc.perform(post("/api/runners/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "ci-runner", "token", token))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ONLINE"));

        // Runner-scoped endpoints work with X-Forge-Runner-Token
        mockMvc.perform(post("/api/runners/" + runnerId + "/heartbeat")
                        .header("X-Forge-Runner-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        // A different user cannot revoke Alice's runner
        mockMvc.perform(delete("/api/runners/" + runnerId)
                        .header("Authorization", "Bearer " + bob.accessToken()))
                .andExpect(status().isNotFound());

        // Owner revokes the credential
        mockMvc.perform(delete("/api/runners/" + runnerId)
                        .header("Authorization", "Bearer " + alice.accessToken()))
                .andExpect(status().isNoContent());

        // Revoked credential no longer authenticates the runner
        mockMvc.perform(post("/api/runners/" + runnerId + "/heartbeat")
                        .header("X-Forge-Runner-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }
}