package com.forgeci.server.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.forgeci.dto.JobClaim;
import com.forgeci.server.application.PipelineRunService;
import com.forgeci.server.application.PipelineService;
import com.forgeci.server.application.ProjectService;
import com.forgeci.server.application.RunnerService;
import com.forgeci.server.entity.PipelineEntity;
import com.forgeci.server.entity.ProjectEntity;
import com.forgeci.server.entity.PipelineRunEntity;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Verifies that concurrent runners claiming jobs never receive the same job
 * twice (FOR UPDATE SKIP LOCKED).
 */
class ConcurrentClaimingIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ProjectService projectService;
    @Autowired
    private PipelineService pipelineService;
    @Autowired
    private PipelineRunService runService;
    @Autowired
    private RunnerService runnerService;

    @Test
    void twoRunnersNeverReceiveTheSameJob() throws Exception {
        UUID ownerId = createUser("concurrent-owner@example.com");
        ProjectEntity project = projectService.create(ownerId, "concurrent-repo",
                "https://example.com/concurrent.git", "main");
        PipelineEntity pipeline = pipelineService.create(ownerId, project.getId(), """
                name: Concurrent
                image: alpine
                jobs:
                  job0: { commands: [echo 0] }
                  job1: { commands: [echo 1] }
                  job2: { commands: [echo 2] }
                  job3: { commands: [echo 3] }
                  job4: { commands: [echo 4] }
                  job5: { commands: [echo 5] }
                  job6: { commands: [echo 6] }
                  job7: { commands: [echo 7] }
                """);
        PipelineRunEntity run = runService.start(ownerId, pipeline.getId(), null);

        UUID runnerA = createRunner(ownerId, "runner-A");
        UUID runnerB = createRunner(ownerId, "runner-B");

        int perRunner = 4;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        Set<UUID> claimed = ConcurrentHashMap.newKeySet();
        List<Exception> errors = new java.util.concurrent.CopyOnWriteArrayList<>();

        Runnable poller = () -> {
            UUID runnerId = Thread.currentThread().getName().startsWith("A") ? runnerA : runnerB;
            try {
                startLatch.await();
                for (int i = 0; i < perRunner; i++) {
                    var claim = runnerService.claimNextJob(runnerId);
                    if (claim.isPresent()) {
                        boolean added = claimed.add(claim.get().jobId());
                        if (!added) {
                            errors.add(new IllegalStateException("Duplicate job claim: " + claim.get().jobId()));
                        }
                    }
                }
            } catch (Exception e) {
                errors.add(e);
            } finally {
                done.countDown();
            }
        };

        pool.submit(() -> { Thread.currentThread().setName("A" + System.nanoTime()); poller.run(); });
        pool.submit(() -> { Thread.currentThread().setName("B" + System.nanoTime()); poller.run(); });

        startLatch.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS), "Claiming did not finish in time");
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

        assertTrue(errors.isEmpty(), "Concurrency errors: " + errors);
        assertEquals(8, claimed.size(), "All 8 jobs should be claimed exactly once");
    }

    @Test
    void independentJobsBecomeReadyInParallel() throws Exception {
        UUID ownerId = createUser("parallel-owner@example.com");
        ProjectEntity project = projectService.create(ownerId, "parallel-repo",
                "https://example.com/parallel.git", "main");
        PipelineEntity pipeline = pipelineService.create(ownerId, project.getId(), """
                name: Parallel
                image: alpine
                jobs:
                  a: { commands: [echo a] }
                  b: { commands: [echo b] }
                  c: { commands: [echo c] }
                """);
        PipelineRunEntity run = runService.start(ownerId, pipeline.getId(), null);

        UUID runnerId = createRunner(ownerId, "parallel-runner");

        Set<UUID> claimed = ConcurrentHashMap.newKeySet();
        ExecutorService pool = Executors.newFixedThreadPool(3);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(3);
        for (int i = 0; i < 3; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    var claim = runnerService.claimNextJob(runnerId);
                    claim.ifPresent(c -> claimed.add(c.jobId()));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(15, TimeUnit.SECONDS));
        pool.shutdown();
        assertEquals(3, claimed.size(), "Three independent jobs should be claimable concurrently");
    }
}