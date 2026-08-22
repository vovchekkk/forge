package com.forgeci.runner.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.forgeci.dto.JobClaim;
import com.forgeci.dto.JobResult;
import com.forgeci.model.JobStatus;
import com.forgeci.model.RunnerStatus;
import com.forgeci.runner.config.ForgeRunnerProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


class ServerApiClientTest {

    private HttpServer server;
    private ServerApiClient client;
    private final List<String> requests = new ArrayList<>();
    private String body;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", this::handle);
        server.start();
        String url = "http://localhost:" + server.getAddress().getPort();
        client = new ServerApiClient(new ForgeRunnerProperties(
                new ForgeRunnerProperties.Server(url, "secret-token"),
                new ForgeRunnerProperties.Runner("test-runner", null, null),
                null));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String auth = exchange.getRequestHeaders().getFirst("X-Forge-Runner-Token");
        requests.add(path + "|auth=" + auth);
        byte[] in = exchange.getRequestBody().readAllBytes();
        body = new String(in, StandardCharsets.UTF_8);

        String response = switch (path) {
            case "/api/runners/register" -> """
                    {"runnerId":"99999999-9999-9999-9999-999999999999","name":"test-runner","status":"ONLINE"}
                    """;
            case "/api/runners/99999999-9999-9999-9999-999999999999/jobs/next" ->
                    """
                    {"jobId":"11111111-1111-1111-1111-111111111111","jobName":"build","pipelineRunId":"22222222-2222-2222-2222-222222222222",
                     "repositoryUrl":"https://example.com/repo.git","revision":"main","image":"eclipse-temurin:21",
                     "commands":["mvn test"],"environment":{"KEY":"value"},"timeoutSeconds":60}
                    """;
            case "/api/runners/99999999-9999-9999-9999-999999999999/jobs/11111111-1111-1111-1111-111111111111/status" ->
                    """
                    {"jobId":"11111111-1111-1111-1111-111111111111","status":"RUNNING"}
                    """;
            default -> "";
        };
        byte[] out = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        if (path.contains("/jobs/next")) {
            exchange.sendResponseHeaders(200, out.length);
        } else if (response.isEmpty()) {
            exchange.sendResponseHeaders(204, -1);
        } else {
            exchange.sendResponseHeaders(200, out.length);
        }
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(out);
        }
    }

    @Test
    void registerSendsCredentialAndStoresRunnerId() {
        UUID id = client.register("test-runner");
        assertThat(id).isEqualTo(UUID.fromString("99999999-9999-9999-9999-999999999999"));
        assertThat(requests).anyMatch(r -> r.startsWith("/api/runners/register|"));
        assertThat(body).contains("secret-token");
    }

    @Test
    void heartbeatSendsAuthHeader() {
        client.register("test-runner");
        client.heartbeat(RunnerStatus.ONLINE, null);
        assertThat(requests).anyMatch(r -> r.startsWith("/api/runners/99999999-9999-9999-9999-999999999999/heartbeat|auth=secret-token"));
    }

    @Test
    void nextJobParsesClaim() {
        client.register("test-runner");
        Optional<JobClaim> claim = client.nextJob();
        assertThat(claim).isPresent();
        JobClaim job = claim.get();
        assertThat(job.jobId()).isEqualTo(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        assertThat(job.commands()).containsExactly("mvn test");
        assertThat(job.environment()).containsEntry("KEY", "value");
        assertThat(job.image()).isEqualTo("eclipse-temurin:21");
    }

    @Test
    void appendLogsSkipsEmptyList() {
        client.register("test-runner");
        client.appendLogs(UUID.fromString("11111111-1111-1111-1111-111111111111"), List.of());
        assertThat(requests).noneMatch(r -> r.contains("/logs"));
    }

    @Test
    void jobStatusParsesStatus() {
        client.register("test-runner");
        JobStatus status = client.jobStatus(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        assertThat(status).isEqualTo(JobStatus.RUNNING);
    }

    @Test
    void reportResultSendsJobIdAndStatus() {
        client.register("test-runner");
        client.reportResult(new JobResult(UUID.fromString("11111111-1111-1111-1111-111111111111"),
                JobStatus.SUCCESS, 0, null));
        assertThat(requests).anyMatch(r -> r.contains("/jobs/11111111-1111-1111-1111-111111111111/result|auth=secret-token"));
        assertThat(body).contains("\"status\":\"SUCCESS\"");
    }

    @Test
    void nextJobReturnsEmptyOnFailureWithoutThrowing() {
        client.register("test-runner");
        
        client = new ServerApiClient(new ForgeRunnerProperties(
                new ForgeRunnerProperties.Server("http://localhost:1", "secret-token"),
                new ForgeRunnerProperties.Runner("test-runner", null, null),
                null));
        Optional<JobClaim> claim = client.nextJob();
        assertThat(claim).isEmpty();
    }
}
