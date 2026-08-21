package com.forgeci.runner.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceManagerTest {

    @TempDir
    Path tempDir;

    private UUID jobId() {
        return UUID.fromString("11111111-1111-1111-1111-111111111111");
    }

    @Test
    void createMakesJobDirectoryUnderRoot() throws IOException {
        WorkspaceManager manager = new WorkspaceManager(tempDir);
        Path workspace = manager.create(jobId());
        assertThat(workspace).isDirectory();
        assertThat(workspace).isEqualTo(tempDir.resolve(jobId().toString()));
    }

    @Test
    void createRecreatesRootIfMissing() throws IOException {
        Path missingRoot = tempDir.resolve("does-not-exist");
        WorkspaceManager manager = new WorkspaceManager(missingRoot);
        Path workspace = manager.create(jobId());
        assertThat(workspace).isDirectory();
    }

    @Test
    void createFailsWhenRootIsAFile() throws IOException {
        Path fileRoot = tempDir.resolve("file-root");
        Files.createFile(fileRoot);
        assertThatThrownBy(() -> new WorkspaceManager(fileRoot))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void createReusesStaleWorkspaceByDeletingIt() throws IOException {
        WorkspaceManager manager = new WorkspaceManager(tempDir);
        Path workspace = manager.create(jobId());
        Files.writeString(workspace.resolve("file.txt"), "stale");
        Path again = manager.create(jobId());
        assertThat(again).isDirectory();
        assertThat(again.resolve("file.txt")).doesNotExist();
    }

    @Test
    void cleanupDeletesWorkspace() throws IOException {
        WorkspaceManager manager = new WorkspaceManager(tempDir);
        Path workspace = manager.create(jobId());
        manager.cleanup(jobId());
        assertThat(workspace).doesNotExist();
    }

    @Test
    void cleanupIsNoopForMissingWorkspace() {
        WorkspaceManager manager = new WorkspaceManager(tempDir);
        manager.cleanup(jobId());
        assertThat(tempDir.resolve(jobId().toString())).doesNotExist();
    }
}
