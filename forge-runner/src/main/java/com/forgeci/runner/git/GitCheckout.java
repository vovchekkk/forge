package com.forgeci.runner.git;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
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

    
    public void cloneAndCheckout(String repositoryUrl, String revision, Path workspace) throws IOException {
        try {
            if (Files.exists(workspace)) {
                deleteRecursively(workspace);
            }
            Files.createDirectories(workspace.getParent());

            cloneWithRetries(repositoryUrl, workspace);

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

    
    private void cloneWithRetries(String repositoryUrl, Path workspace) throws IOException {
        IOException last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            Git git = null;
            try {
                if (Files.exists(workspace)) {
                    deleteRecursively(workspace);
                }
                git = Git.cloneRepository()
                        .setURI(repositoryUrl)
                        .setDirectory(workspace.toFile())
                        .setCloneAllBranches(true)
                        .setCredentialsProvider(credentials())
                        .call();
                return;
            } catch (GitAPIException e) {
                last = new IOException("Git clone failed for " + repositoryUrl, e);
                log.warn("Git clone of {} failed on attempt {}/3: {}", repositoryUrl, attempt, e.getMessage());
                if (attempt < 3) {
                    try {
                        Thread.sleep(1000L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw last;
                    }
                }
            } finally {
                if (git != null) {
                    git.close();
                }
            }
        }
        throw last;
    }

    private String resolveRevision(Repository repository, String revision) throws IOException {
        ObjectId objectId = repository.resolve(revision);
        if (objectId != null) {
            return objectId.getName();
        }
        
        ObjectId ref = repository.resolve("refs/heads/" + revision);
        if (ref != null) {
            return ref.getName();
        }
        
        ObjectId remote = repository.resolve("refs/remotes/origin/" + revision);
        if (remote != null) {
            return remote.getName();
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
        if (!Files.isDirectory(path)) {
            return;
        }
        IOException last = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                try (var stream = Files.walk(path)) {
                    stream.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
                }
                return;
            } catch (RuntimeException e) {
                Throwable cause = e.getCause();
                if (cause instanceof IOException io) {
                    last = io;
                } else {
                    throw e;
                }
            }
            if (attempt == 0) {
                System.gc();
            }
            try {
                Thread.sleep(100L * (attempt + 1));
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw last;
            }
        }
        throw last;
    }
}