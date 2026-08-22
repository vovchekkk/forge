package com.forgeci.runner.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;


class GitCheckoutTest {

    @TempDir
    Path tempDir;

    private Path createRemoteRepo(String branch, String fileName, String content) throws Exception {
        Path remote = tempDir.resolve("remote-" + branch.replace("/", "-"));
        Files.createDirectories(remote);
        try (Git git = Git.init().setDirectory(remote.toFile()).call()) {
            Files.writeString(remote.resolve(fileName), content);
            git.add().addFilepattern(".").call();
            git.commit().setMessage("initial").call();
            String current = git.getRepository().getBranch();
            if (!current.equals(branch)) {
                git.branchCreate().setName(branch).call();
            }
        }
        return remote;
    }

    @Test
    void clonesDefaultBranchWhenRevisionNull() throws Exception {
        Path remote = createRemoteRepo("main", "hello.txt", "world");
        Path workspace = tempDir.resolve("ws");
        new GitCheckout().cloneAndCheckout("file://" + remote.toString().replace("\\", "/"),
                null, workspace);
        assertThat(workspace.resolve("hello.txt")).hasContent("world");
    }

    @Test
    void checksOutSpecificBranch() throws Exception {
        Path remote = createRemoteRepo("feature/x", "file.txt", "content");
        Path workspace = tempDir.resolve("ws");
        new GitCheckout().cloneAndCheckout("file://" + remote.toString().replace("\\", "/"),
                "feature/x", workspace);
        assertThat(workspace.resolve("file.txt")).hasContent("content");
    }

    @Test
    void checksOutCommitSha() throws Exception {
        Path remote = tempDir.resolve("remote-sha");
        Files.createDirectories(remote);
        String sha;
        try (Git git = Git.init().setDirectory(remote.toFile()).call()) {
            Files.writeString(remote.resolve("a.txt"), "a");
            git.add().addFilepattern(".").call();
            sha = git.commit().setMessage("m1").call().getName();
            Files.writeString(remote.resolve("b.txt"), "b");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("m2").call();
        }
        Path workspace = tempDir.resolve("ws");
        new GitCheckout().cloneAndCheckout("file://" + remote.toString().replace("\\", "/"),
                sha, workspace);
        assertThat(workspace.resolve("a.txt")).hasContent("a");
        assertThat(workspace.resolve("b.txt")).doesNotExist();
    }

    @Test
    void rejectsUnknownRevision() throws Exception {
        Path remote = createRemoteRepo("main", "hello.txt", "world");
        Path workspace = tempDir.resolve("ws");
        assertThatThrownBy(() -> new GitCheckout()
                .cloneAndCheckout("file://" + remote.toString().replace("\\", "/"),
                        "no-such-ref", workspace))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Revision not found");
    }

    @Test
    void rejectsUnreachableUrl() {
        Path workspace = tempDir.resolve("ws");
        assertThatThrownBy(() -> new GitCheckout()
                .cloneAndCheckout("file:///nonexistent/path/to/repo", null, workspace))
                .isInstanceOf(IOException.class);
    }
}
