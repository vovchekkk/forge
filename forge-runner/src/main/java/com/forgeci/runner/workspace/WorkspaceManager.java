package com.forgeci.runner.workspace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages per-job workspace directories. Each job gets its own directory under
 * a configured root, which is deleted after the job finishes.
 */
public class WorkspaceManager {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceManager.class);

    private final Path root;

    public WorkspaceManager(Path root) {
        this.root = root;
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create workspace root " + root, e);
        }
    }

    public Path create(UUID jobId) throws IOException {
        Path workspace = root.resolve(jobId.toString());
        if (Files.exists(workspace)) {
            deleteRecursively(workspace);
        }
        Files.createDirectories(workspace);
        return workspace;
    }

    public void cleanup(UUID jobId) {
        Path workspace = root.resolve(jobId.toString());
        try {
            if (Files.exists(workspace)) {
                deleteRecursively(workspace);
            }
        } catch (IOException e) {
            log.warn("Failed to clean up workspace {}", workspace, e);
        }
    }

    private void deleteRecursively(Path path) throws IOException {
        try (var stream = Files.walk(path)) {
            stream.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
}