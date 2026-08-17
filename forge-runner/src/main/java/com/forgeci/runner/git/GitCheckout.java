package com.forgeci.runner.git;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GitCheckout {

    private static final Logger log = LoggerFactory.getLogger(GitCheckout.class);

    private final String username;
    private final String password;

    public GitCheckout() {
        this(null, null);
    }

    public GitCheckout(String username, String password) {
        this.username = username;
        this.password = password;
    }

    /**
     * Clone the repository into {@code workspace} and check out {@code revision}.
     * {@code revision} may be a branch name, tag, or commit SHA. When null, the
     * default branch (HEAD) is used.
     */
    public void cloneAndCheckout(String repositoryUrl, String revision, Path workspace) throws IOException {
        try {
            if (Files.exists(workspace)) {
                deleteRecursively(workspace);
            }
            Files.createDirectories(workspace.getParent());

            Git git = null;
            try {
                git = Git.cloneRepository()
                        .setURI(repositoryUrl)
                        .setDirectory(workspace.toFile())
                        .setCloneAllBranches(true)
                        .setCredentialsProvider(credentials())
                        .call();
            } finally {
                if (git != null) {
                    git.close();
                }
            }

            if (revision != null && !revision.isBlank()) {
                try (Git git2 = Git.open(workspace.toFile())) {
                    git2.checkout().setName(resolveRevision(git2.getRepository(), revision)).call();
                }
            }
            log.info("Checked out {}@{} into {}", repositoryUrl, revision == null ? "HEAD" : revision, workspace);
        } catch (GitAPIException e) {
            throw new IOException("Git checkout failed for " + repositoryUrl, e);
        }
    }

    private String resolveRevision(Repository repository, String revision) throws IOException {
        ObjectId objectId = repository.resolve(revision);
        if (objectId != null) {
            return objectId.getName();
        }
        // Fall back to a raw SHA-ish ref like refs/heads/<revision>
        ObjectId ref = repository.resolve("refs/heads/" + revision);
        if (ref != null) {
            return ref.getName();
        }
        throw new IOException("Revision not found in repository: " + revision);
    }

    private UsernamePasswordCredentialsProvider credentials() {
        if (username != null && password != null) {
            return new UsernamePasswordCredentialsProvider(username, password);
        }
        return null;
    }

    private void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
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
}