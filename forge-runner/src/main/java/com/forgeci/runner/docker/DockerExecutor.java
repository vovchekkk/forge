package com.forgeci.runner.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PullResponseItem;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.command.LogContainerResultCallback;
import java.time.Duration;
import java.time.Instant;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executes job commands inside ephemeral Docker containers via the Docker
 * Engine API. Each command runs in its own container bound to the job
 * workspace. Containers are non-privileged, never mount the Docker socket,
 * and are always removed (also on failure and timeout).
 */
public class DockerExecutor {

    private static final Logger log = LoggerFactory.getLogger(DockerExecutor.class);
    private static final String WORKSPACE_DIR = "/workspace";

    private final DockerClient dockerClient;
    private final String workspaceVolumeName;

    public DockerExecutor(DockerClient dockerClient) {
        this(dockerClient, null);
    }

    public DockerExecutor(DockerClient dockerClient, String workspaceVolumeName) {
        this.dockerClient = dockerClient;
        this.workspaceVolumeName = workspaceVolumeName;
    }

    /**
     * Run job commands sequentially. Returns the first failing result. On
     * timeout the running container is killed and cleaned up. When
     * {@code isCancelled} reports true the running container is killed and a
     * canceled result is returned.
     */
    public CommandResult runJobCommands(String image, List<String> commands, Map<String, String> environment,
                                        String hostWorkspace, Duration timeout, BooleanSupplier isCancelled,
                                        Consumer<String> onLog) {
        List<String> allOutput = new ArrayList<>();
        for (int i = 0; i < commands.size(); i++) {
            String command = commands.get(i);
            log.info("Running command {}: {}", i, command);
            CommandResult result = runSingleCommand(image, command, environment, hostWorkspace, timeout,
                    isCancelled, onLog);
            allOutput.addAll(result.output());
            if (result.timedOut()) {
                return new CommandResult(-1, allOutput, true, "Command timed out after " + timeout);
            }
            if (result.canceled()) {
                return new CommandResult(-1, allOutput, false, "Job was canceled", true);
            }
            if (result.exitCode() != 0) {
                return new CommandResult(result.exitCode(), allOutput, false,
                        "Command failed with exit code " + result.exitCode() + ": " + command);
            }
        }
        return new CommandResult(0, allOutput, false, null);
    }

    private CommandResult runSingleCommand(String image, String command, Map<String, String> environment,
                                           String hostWorkspace, Duration timeout, BooleanSupplier isCancelled,
                                           Consumer<String> onLog) {
        ensureImage(image);
        String containerId = null;
        try {
            containerId = createContainer(image, command, environment, hostWorkspace);
            dockerClient.startContainerCmd(containerId).exec();
            log.info("Container {} started for command: {}", containerId, command);

            LoggingCallback logging = new LoggingCallback(onLog);
            var logFollow = dockerClient.logContainerCmd(containerId)
                    .withStdOut(true)
                    .withStdErr(true)
                    .withFollowStream(true)
                    .withTailAll()
                    .exec(logging);

            long deadline = timeout == null ? Long.MAX_VALUE
                    : Instant.now().plus(timeout).toEpochMilli();
            while (Boolean.TRUE.equals(dockerClient.inspectContainerCmd(containerId).exec().getState().getRunning())) {
                if (isCancelled != null && isCancelled.getAsBoolean()) {
                    log.warn("Cancellation requested, killing container {}", containerId);
                    dockerClient.killContainerCmd(containerId).exec();
                    try {
                        logFollow.awaitCompletion(5, TimeUnit.SECONDS);
                    } catch (InterruptedException ignore) {
                        Thread.currentThread().interrupt();
                    }
                    return new CommandResult(-1, logging.getLines(), false, "Job was canceled", true);
                }
                if (System.currentTimeMillis() > deadline) {
                    log.warn("Command timed out, killing container {}", containerId);
                    dockerClient.killContainerCmd(containerId).exec();
                    return new CommandResult(-1, logging.getLines(), true, "Command timed out after " + timeout, false);
                }
                Thread.sleep(500);
            }
            try {
                logFollow.awaitCompletion(10, TimeUnit.SECONDS);
            } catch (InterruptedException ignore) {
                Thread.currentThread().interrupt();
            }

            InspectContainerResponse finished = dockerClient.inspectContainerCmd(containerId).exec();
            Long exit = finished.getState().getExitCodeLong();
            int exitCode = exit == null ? -1 : exit.intValue();
            return new CommandResult(exitCode, logging.getLines(), false, null, false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new CommandResult(-1, List.of(), true, "Execution interrupted", false);
        } catch (Exception e) {
            log.error("Docker execution failed for command: {}", command, e);
            return new CommandResult(-1, List.of(), false, "Docker execution failed: " + e.getMessage(), false);
        } finally {
            if (containerId != null) {
                removeContainer(containerId);
            }
        }
    }

    private static final int IMAGE_PULL_ATTEMPTS = 3;

    private void ensureImage(String image) {
        try {
            dockerClient.inspectImageCmd(image).exec();
            return;
        } catch (Exception ignore) {
            // image not present locally, pull below
        }
        RuntimeException last = null;
        for (int attempt = 1; attempt <= IMAGE_PULL_ATTEMPTS; attempt++) {
            try {
                log.info("Pulling image {} (attempt {}/{})", image, attempt, IMAGE_PULL_ATTEMPTS);
                dockerClient.pullImageCmd(image).exec(new PullImageCallback()).awaitCompletion();
                return;
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Image pull interrupted: " + image, ie);
            } catch (RuntimeException e) {
                last = e;
                log.warn("Image pull {} failed on attempt {}/{}: {}", image, attempt, IMAGE_PULL_ATTEMPTS,
                        e.getMessage());
                try {
                    Thread.sleep(1000L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Image pull interrupted: " + image, ie);
                }
            }
        }
        throw last;
    }

    private String createContainer(String image, String command, Map<String, String> environment,
                                   String hostWorkspace) {
        Volume volume = new Volume(WORKSPACE_DIR);
        HostConfig hostConfig = HostConfig.newHostConfig()
                .withPrivileged(false)
                .withAutoRemove(false);
        String workingDir = WORKSPACE_DIR;
        if (workspaceVolumeName != null && !workspaceVolumeName.isBlank()) {
            // Shared named volume: both the runner and job containers mount it,
            // so the workspace is visible at /workspace/<jobId> inside the job.
            hostConfig = hostConfig.withBinds(new Bind(workspaceVolumeName, volume, AccessMode.rw));
            String jobDir = Path.of(hostWorkspace).getFileName().toString();
            workingDir = WORKSPACE_DIR + "/" + jobDir;
        } else {
            // Fallback: bind the host workspace directory directly.
            hostConfig = hostConfig.withBinds(new Bind(hostWorkspace, volume, AccessMode.rw));
        }

        CreateContainerCmd cmd = dockerClient.createContainerCmd(image)
                .withWorkingDir(workingDir)
                .withCmd("/bin/sh", "-lc", command)
                .withHostConfig(hostConfig);
        if (environment != null && !environment.isEmpty()) {
            List<String> env = environment.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .toList();
            cmd.withEnv(env);
        }
        return cmd.exec().getId();
    }

    private void removeContainer(String containerId) {
        try {
            dockerClient.removeContainerCmd(containerId).withForce(true).exec();
        } catch (Exception e) {
            log.warn("Failed to remove container {}: {}", containerId, e.getMessage());
        }
    }

    /** Collects container stdout/stderr and streams lines to a consumer. */
    private static class LoggingCallback extends LogContainerResultCallback {
        private final List<String> lines = new ArrayList<>();
        private final Consumer<String> onLog;
        private volatile boolean completed = false;

        LoggingCallback(Consumer<String> onLog) {
            this.onLog = onLog;
        }

        @Override
        public void onNext(com.github.dockerjava.api.model.Frame item) {
            String text = new String(item.getPayload());
            for (String line : text.split("\\r?\\n")) {
                if (!line.isEmpty()) {
                    lines.add(line);
                    if (onLog != null) {
                        onLog.accept(line);
                    }
                }
            }
            super.onNext(item);
        }

        @Override
        public void onComplete() {
            completed = true;
            super.onComplete();
        }

        List<String> getLines() { return lines; }
        boolean isCompleted() { return completed; }
    }

    public record CommandResult(int exitCode, List<String> output, boolean timedOut, String errorMessage,
                                boolean canceled) {
        public CommandResult(int exitCode, List<String> output, boolean timedOut, String errorMessage) {
            this(exitCode, output, timedOut, errorMessage, false);
        }
    }
}