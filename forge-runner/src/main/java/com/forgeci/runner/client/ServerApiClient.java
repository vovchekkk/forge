package com.forgeci.runner.client;

import com.forgeci.dto.JobClaim;
import com.forgeci.dto.JobResult;
import com.forgeci.dto.JobStatusResponse;
import com.forgeci.dto.LogChunkRequest;
import com.forgeci.dto.RunnerHeartbeatRequest;
import com.forgeci.dto.RunnerRegistrationRequest;
import com.forgeci.dto.RunnerRegistrationResponse;
import com.forgeci.model.JobStatus;
import com.forgeci.model.RunnerStatus;
import com.forgeci.runner.config.ForgeRunnerProperties;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

public class ServerApiClient {

    private static final Logger log = LoggerFactory.getLogger(ServerApiClient.class);

    private final RestClient restClient;
    private final String baseUrl;
    private final String credential;
    private UUID runnerId;

    public ServerApiClient(ForgeRunnerProperties properties) {
        this.baseUrl = properties.server().url();
        this.credential = properties.server().token();
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    private RestClient.RequestHeadersSpec<?> withCredential(RestClient.RequestHeadersSpec<?> spec) {
        return spec.header(RunnerAuthenticationHeader.NAME, credential);
    }

    public UUID register(String name) {
        RunnerRegistrationResponse response = restClient.post()
                .uri("/api/runners/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RunnerRegistrationRequest(name, credential))
                .retrieve()
                .body(RunnerRegistrationResponse.class);
        this.runnerId = response.runnerId();
        log.info("Registered runner '{}' with id {}", name, runnerId);
        return runnerId;
    }

    public void heartbeat(RunnerStatus status, UUID currentJobId) {
        try {
            withCredential(restClient.post()
                    .uri("/api/runners/{id}/heartbeat", runnerId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new RunnerHeartbeatRequest(status, currentJobId)))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Heartbeat failed: {}", e.getMessage());
        }
    }

    public Optional<JobClaim> nextJob() {
        try {
            return withCredential(restClient.get()
                    .uri("/api/runners/{id}/jobs/next", runnerId))
                    .exchange((request, response) -> {
                        if (response.getStatusCode() == HttpStatus.NO_CONTENT) {
                            return Optional.empty();
                        }
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            return Optional.empty();
                        }
                        return Optional.of(response.bodyTo(JobClaim.class));
                    });
        } catch (Exception e) {
            log.warn("Polling for jobs failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public void appendLogs(UUID jobId, List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        try {
            withCredential(restClient.post()
                    .uri("/api/runners/{id}/jobs/{jobId}/logs", runnerId, jobId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new LogChunkRequest(lines)))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Failed to send logs for job {}: {}", jobId, e.getMessage());
        }
    }

    public void reportResult(JobResult result) {
        try {
            withCredential(restClient.post()
                    .uri("/api/runners/{id}/jobs/{jobId}/result", runnerId, result.jobId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(result))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("Failed to report result for job {}: {}", result.jobId(), e.getMessage());
        }
    }

    /** Returns the current server-side status of a job, or null when unreachable. */
    public JobStatus jobStatus(UUID jobId) {
        try {
            JobStatusResponse response = withCredential(restClient.get()
                    .uri("/api/runners/{id}/jobs/{jobId}/status", runnerId, jobId))
                    .retrieve()
                    .body(JobStatusResponse.class);
            return response == null ? null : response.status();
        } catch (Exception e) {
            log.debug("Failed to fetch status for job {}: {}", jobId, e.getMessage());
            return null;
        }
    }

    private static final class RunnerAuthenticationHeader {
        static final String NAME = "X-Forge-Runner-Token";
    }
}