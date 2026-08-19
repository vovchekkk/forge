package com.forgeci.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.forgeci.model.JobStatus;
import com.forgeci.model.PipelineRunStatus;
import com.forgeci.model.RunnerStatus;
import com.forgeci.runner.client.ServerApiClient;
import com.forgeci.runner.config.ForgeRunnerProperties;
import com.forgeci.runner.docker.DockerExecutor;
import com.forgeci.runner.git.GitCheckout;
import com.forgeci.runner.service.JobRunner;
import com.forgeci.runner.workspace.WorkspaceManager;
import com.forgeci.server.application.PipelineRunService;
import com.forgeci.server.application.PipelineService;
import com.forgeci.server.application.ProjectService;
import com.forgeci.server.entity.JobEntity;
import com.forgeci.server.entity.JobLogEntity;
import com.forgeci.server.entity.PipelineEntity;
import com.forgeci.server.entity.PipelineRunEntity;
import com.forgeci.server.entity.ProjectEntity;
import com.forgeci.server.entity.RunnerEntity;
import com.forgeci.server.repository.JobLogRepository;
import com.forgeci.server.repository.JobRepository;
import com.forgeci.server.repository.PipelineRunRepository;
import com.forgeci.server.repository.RunnerRepository;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * End-to-end pipeline execution: a real server, a real runner, a real git
 * repository, and real Docker containers. Verifies that the whole system works
 * together: project -&gt; pipeline -&gt; run -&gt; claim -&gt; checkout -&gt; docker -&gt;
 * logs -&gt; dependent job -&gt; SUCCESS.
 */
@SpringBootTest(
        classes = com.forgeci.server.ForgeServerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "forge.scheduler.interval=200ms",
                "forge.runner.offline-check-interval=60s",
                "forge.runner.offline-threshold=30s"
        })
class EndToEndPipelineTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ProjectService projectService;
    @Autowired
    private PipelineService pipelineService;
    @Autowired
    private PipelineRunService runService;
    @Autowired
    private JobRepository jobRepository;
    @Autowired
    private JobLogRepository jobLogRepository;
    @Autowired
    private PipelineRunRepository runRepository;
    @Autowired
    private RunnerRepository runnerRepository;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", E2ePostgresConfig.POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", E2ePostgresConfig.POSTGRES::getUsername);
        registry.add("spring.datasource.password", E2ePostgresConfig.POSTGRES::getPassword);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void fullPipelineRunsToSuccessWithRealRunnerAndDocker() throws Exception {
        Path repo = createLocalGitRepo();

        ProjectEntity project = projectService.create("e2e-project", repo.toUri().toString(), "main");
        PipelineEntity pipeline = pipelineService.create(project.getId(), PIPELINE_CONFIG);
        PipelineRunEntity run = runService.start(pipeline.getId(), "main");
        assertEquals(PipelineRunStatus.QUEUED, run.getStatus());

        String token = "e2e-token-" + UUID.randomUUID();
        JobRunner jobRunner = buildRealRunner(token);
        jobRunner.register();

        RunnerEntity runner = runnerRepository.findByToken(token).orElseThrow();
        assertEquals(RunnerStatus.ONLINE, runner.getStatus());

        PipelineRunEntity finished = awaitTerminal(run.getId(), jobRunner);
        assertEquals(PipelineRunStatus.SUCCESS, finished.getStatus(), "Run should finish SUCCESS");

        List<JobEntity> jobs = jobRepository.findByPipelineRunId(run.getId());
        assertEquals(2, jobs.size());
        for (JobEntity job : jobs) {
            assertEquals(JobStatus.SUCCESS, job.getStatus(), "Job " + job.getName() + " should be SUCCESS");
            List<JobLogEntity> logs = jobLogRepository.findByJobIdOrderByCreatedAtAsc(job.getId());
            assertFalse(logs.isEmpty(), "Job " + job.getName() + " should have logs");
            String output = logs.stream().map(JobLogEntity::getContent).reduce("", (a, b) -> a + "\n" + b);
            assertTrue(output.contains(job.getName().equals("build") ? "building" : "testing"),
                    "Job " + job.getName() + " log should contain command output, was: " + output);
        }

        RunnerEntity refreshed = runnerRepository.findById(runner.getId()).orElseThrow();
        assertEquals(RunnerStatus.ONLINE, refreshed.getStatus());
        assertNotNull(runRepository.findById(run.getId()).orElseThrow().getFinishedAt());
    }

    private JobRunner buildRealRunner(String token) throws IOException {
        Path workspaceRoot = Files.createTempDirectory("forge-e2e-workspace");
        ForgeRunnerProperties properties = new ForgeRunnerProperties(
                new ForgeRunnerProperties.Server("http://localhost:" + port, token),
                new ForgeRunnerProperties.Runner("e2e-runner", workspaceRoot.toString()),
                new ForgeRunnerProperties.Git(null, null));
        return new JobRunner(
                new ServerApiClient(properties),
                new GitCheckout(),
                new DockerExecutor(createDockerClient()),
                new WorkspaceManager(workspaceRoot));
    }

    private PipelineRunEntity awaitTerminal(UUID runId, JobRunner jobRunner) throws InterruptedException {
        long deadline = System.currentTimeMillis() + Duration.ofMinutes(4).toMillis();
        while (System.currentTimeMillis() < deadline) {
            PipelineRunEntity run = runRepository.findById(runId).orElseThrow();
            if (run.getStatus() != PipelineRunStatus.QUEUED
                    && run.getStatus() != PipelineRunStatus.RUNNING) {
                return run;
            }
            jobRunner.poll();
            Thread.sleep(200);
        }
        throw new AssertionError("Run " + runId + " did not reach terminal state in time");
    }

    private Path createLocalGitRepo() throws Exception {
        Path repo = Files.createTempDirectory("forge-e2e-git");
        try (Git git = Git.init().setDirectory(repo.toFile()).call()) {
            Files.writeString(repo.resolve("README.md"), "# e2e\n");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("initial commit")
                    .setAuthor("E2E", "e2e@forge.local")
                    .setCommitter("E2E", "e2e@forge.local")
                    .call();
        }
        return repo;
    }

    private static DockerClient createDockerClient() {
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        DockerHttpClient httpClient = new ZerodepDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(10))
                .responseTimeout(Duration.ofSeconds(120))
                .build();
        return DockerClientImpl.getInstance(config, httpClient);
    }

    private static final String PIPELINE_CONFIG = """
            name: E2E Pipeline
            image: alpine:latest
            jobs:
              build:
                commands:
                  - echo building
              test:
                needs: [build]
                commands:
                  - echo testing
            """;
}