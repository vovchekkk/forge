package com.forgeci.runner.workspace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class WorkspaceManager {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceManager.class);

    private final Path root;
    private final Thread cleanupThread;

    public WorkspaceManager(Path root) {
        this.root = root;
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create workspace root " + root, e);
        }
        this.cleanupThread = new Thread(this::cleanupOnShutdown, "workspace-shutdown-cleanup");
        Runtime.getRuntime().addShutdownHook(cleanupThread);
    }

    
    private void cleanupOnShutdown() {
        try {
            if (!Files.exists(root)) {
                return;
            }
            String command = "cmd.exe /c ping -n 3 127.0.0.1 > nul & rd /s /q \"" + root + "\"";
            new ProcessBuilder("cmd.exe", "/c", "ping -n 3 127.0.0.1 > nul & rd /s /q \"" + root + "\"")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (IOException e) {
            log.warn("Failed to schedule workspace cleanup for {}", root, e);
        }
    }

    public Path create(UUID jobId) throws IOException {
        Path workspace = root.resolve(jobId.toString());
        if (Files.exists(workspace)) {
            try {
                deleteRecursively(workspace);
            } catch (RuntimeException e) {
                log.warn("Stale workspace {} locked, reusing it: {}", workspace, e.getCause().getMessage());
            }
        }
        Files.createDirectories(workspace);
        return workspace;
    }

    public void cleanup(UUID jobId) {
        Path workspace = root.resolve(jobId.toString());
        if (!Files.exists(workspace)) {
            return;
        }
        try {
            deleteRecursively(workspace);
        } catch (RuntimeException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            log.warn("Workspace {} is locked, will be cleaned on JVM shutdown: {}", workspace, cause.getMessage());
        }
    }

    private void deleteRecursively(Path path) {
        try (var stream = Files.walk(path)) {
            stream.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}