package com.forgeci.runner.docker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Integration test against a real Docker daemon. The runner requires Docker
 * anyway, so the container orchestration is verified end to end. When no
 * daemon is reachable the tests are skipped rather than failed.
 */
class DockerExecutorTest {

    private static final String IMAGE = "alpine:3.20";
    private static DockerClient dockerClient;
    private static DockerExecutor executor;

    @BeforeAll
    static void setUp() {
        try {
            DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
            DockerHttpClient http = new ZerodepDockerHttpClient.Builder()
                    .dockerHost(config.getDockerHost())
                    .sslConfig(config.getSSLConfig())
                    .build();
            dockerClient = DockerClientImpl.getInstance(config, http);
            dockerClient.pingCmd().exec();
            executor = new DockerExecutor(dockerClient);
        } catch (Exception e) {
            dockerClient = null;
        }
        assumeTrue(dockerClient != null, "Docker daemon not available, skipping integration test");
        try {
            dockerClient.inspectImageCmd(IMAGE).exec();
        } catch (Exception e) {
            try {
                dockerClient.pullImageCmd(IMAGE).start().awaitCompletion();
            } catch (Exception ignore) {
                assumeTrue(false, "Cannot pull " + IMAGE);
            }
        }
    }

    @AfterAll
    static void tearDown() {
        if (dockerClient != null) {
            try {
                dockerClient.close();
            } catch (Exception ignore) {
                // best-effort shutdown
            }
        }
    }

    private DockerExecutor.CommandResult run(List<String> commands, Duration timeout) {
        AtomicReference<String> log = new AtomicReference<>("");
        return executor.runJobCommands(IMAGE, commands, Map.of(), tempWorkspace(),
                timeout, () -> false, line -> log.set(log.get() + line + "\n"));
    }

    private String tempWorkspace() {
        try {
            return java.nio.file.Files.createTempDirectory("forge-ws").toString();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void runsSuccessfulCommand() {
        DockerExecutor.CommandResult result = run(List.of("echo hello"), Duration.ofSeconds(30));
        assertThat(result.exitCode()).isZero();
        assertThat(result.output()).contains("hello");
        assertThat(result.timedOut()).isFalse();
        assertThat(result.canceled()).isFalse();
    }

    @Test
    void capturesCommandOutputLines() {
        DockerExecutor.CommandResult result = run(List.of("printf 'a\\nb\\n'"), Duration.ofSeconds(30));
        assertThat(result.output()).contains("a", "b");
    }

    @Test
    void stopsOnFirstFailingCommand() {
        DockerExecutor.CommandResult result = run(
                List.of("echo first", "false", "echo never-reached"), Duration.ofSeconds(30));
        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.output()).contains("first");
        assertThat(result.output()).noneMatch(l -> l.contains("never-reached"));
    }

    @Test
    void sequentialCommandsAllRunOnSuccess() {
        DockerExecutor.CommandResult result = run(
                List.of("echo one", "echo two", "echo three"), Duration.ofSeconds(30));
        assertThat(result.exitCode()).isZero();
        assertThat(result.output()).contains("one", "two", "three");
    }

    @Test
    void terminatesLongRunningCommandOnTimeout() {
        DockerExecutor.CommandResult result = run(
                List.of("sleep 60"), Duration.ofSeconds(3));
        assertThat(result.timedOut()).isTrue();
        assertThat(result.exitCode()).isEqualTo(-1);
    }

    @Test
    void killsContainerWhenCancellationRequested() throws Exception {
        // Cancel after the container is definitely running to avoid a race
        // between startContainer and the first inspection.
        java.util.concurrent.atomic.AtomicBoolean cancel = new java.util.concurrent.atomic.AtomicBoolean(false);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newSingleThreadExecutor();
        pool.submit(() -> {
            try {
                Thread.sleep(1500);
                cancel.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        try {
            DockerExecutor.CommandResult result = executor.runJobCommands(IMAGE,
                    List.of("sleep 60"), Map.of(), tempWorkspace(), Duration.ofSeconds(30),
                    cancel::get, line -> { });
            assertThat(result.canceled()).isTrue();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void environmentVariablesAreExposedToCommand() {
        DockerExecutor.CommandResult result = executor.runJobCommands(IMAGE,
                List.of("echo $MY_VAR"), Map.of("MY_VAR", "forge"), tempWorkspace(),
                Duration.ofSeconds(30), () -> false, line -> { });
        assertThat(result.output()).contains("forge");
    }

    @Test
    void workspaceIsBoundIntoContainer() throws Exception {
        java.nio.file.Path ws = java.nio.file.Files.createTempDirectory("forge-ws");
        java.nio.file.Files.writeString(ws.resolve("payload.txt"), "bound-content");
        AtomicBoolean sawFile = new AtomicBoolean(false);
        DockerExecutor.CommandResult result = executor.runJobCommands(IMAGE,
                List.of("cat /workspace/payload.txt"), Map.of(), ws.toString(),
                Duration.ofSeconds(30), () -> false, line -> {
                    if (line.contains("bound-content")) {
                        sawFile.set(true);
                    }
                });
        assertThat(result.exitCode()).isZero();
        assertThat(sawFile.get()).isTrue();
    }
}
