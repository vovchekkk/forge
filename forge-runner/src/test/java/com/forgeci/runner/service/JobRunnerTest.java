package com.forgeci.runner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.forgeci.dto.JobClaim;
import com.forgeci.dto.JobResult;
import com.forgeci.model.JobStatus;
import com.forgeci.model.RunnerStatus;
import com.forgeci.runner.client.ServerApiClient;
import com.forgeci.runner.config.ForgeRunnerProperties;
import com.forgeci.runner.docker.DockerExecutor;
import com.forgeci.runner.git.GitCheckout;
import com.forgeci.runner.workspace.WorkspaceManager;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobRunnerTest {

    private ServerApiClient apiClient;
    private GitCheckout gitCheckout;
    private DockerExecutor dockerExecutor;
    private WorkspaceManager workspaceManager;
    private JobRunner runner;

    private static final UUID JOB_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @BeforeEach
    void setUp() {
        apiClient = mock(ServerApiClient.class);
        gitCheckout = mock(GitCheckout.class);
        dockerExecutor = mock(DockerExecutor.class);
        workspaceManager = mock(WorkspaceManager.class);
        runner = new JobRunner(apiClient, gitCheckout, dockerExecutor, workspaceManager,
                new ForgeRunnerProperties(
                        new ForgeRunnerProperties.Server("http://localhost", "tok"),
                        new ForgeRunnerProperties.Runner("test-runner", null, null),
                        null));
    }

    private JobClaim claim() {
        return new JobClaim(JOB_ID, "build", UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "https://example.com/repo.git", "main", "eclipse-temurin:21",
                List.of("mvn test"), java.util.Map.of(), 60);
    }

    @Test
    void registerOnlyOnce() {
        runner.register();
        runner.register();
        verify(apiClient).register("test-runner");
    }

    @Test
    void pollDoesNothingBeforeRegister() {
        runner.poll();
        verify(apiClient, never()).nextJob();
    }

    @Test
    void pollSkipsWhenBusy() {
        runner.register();
        when(apiClient.nextJob()).thenReturn(Optional.of(claim()));
        runner.poll();
        // simulate busy: current job set, subsequent poll must not claim again
        when(apiClient.nextJob()).thenReturn(Optional.empty());
        runner.poll();
        verify(apiClient, never()).jobStatus(any());
    }

    @Test
    void pollExecutesClaimedJobAndReportsSuccess() throws Exception {
        runner.register();
        when(apiClient.nextJob()).thenReturn(Optional.of(claim()));
        when(workspaceManager.create(JOB_ID)).thenReturn(Path.of("ws"));
        when(dockerExecutor.runJobCommands(anyString(), any(), any(), anyString(), any(),
                any(), any())).thenReturn(new DockerExecutor.CommandResult(0, List.of("ok"), false, null));

        runner.poll();

        ArgumentCaptor<JobResult> captor = ArgumentCaptor.forClass(JobResult.class);
        verify(apiClient).reportResult(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(JobStatus.SUCCESS);
        assertThat(captor.getValue().exitCode()).isZero();
        verify(workspaceManager).cleanup(JOB_ID);
    }

    @Test
    void pollReportsFailureOnNonZeroExit() throws Exception {
        runner.register();
        when(apiClient.nextJob()).thenReturn(Optional.of(claim()));
        when(workspaceManager.create(JOB_ID)).thenReturn(Path.of("ws"));
        when(dockerExecutor.runJobCommands(anyString(), any(), any(), anyString(), any(),
                any(), any())).thenReturn(new DockerExecutor.CommandResult(2, List.of("err"), false, "boom"));

        runner.poll();

        ArgumentCaptor<JobResult> captor = ArgumentCaptor.forClass(JobResult.class);
        verify(apiClient).reportResult(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(JobStatus.FAILED);
        assertThat(captor.getValue().exitCode()).isEqualTo(2);
    }

    @Test
    void pollReportsCanceledWhenJobCanceled() throws Exception {
        runner.register();
        when(apiClient.nextJob()).thenReturn(Optional.of(claim()));
        when(workspaceManager.create(JOB_ID)).thenReturn(Path.of("ws"));
        when(dockerExecutor.runJobCommands(anyString(), any(), any(), anyString(), any(),
                any(), any())).thenReturn(new DockerExecutor.CommandResult(-1, List.of(), false, "canceled", true));

        runner.poll();

        ArgumentCaptor<JobResult> captor = ArgumentCaptor.forClass(JobResult.class);
        verify(apiClient).reportResult(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(JobStatus.CANCELED);
    }

    @Test
    void pollReportsTimedOutAsFailed() throws Exception {
        runner.register();
        when(apiClient.nextJob()).thenReturn(Optional.of(claim()));
        when(workspaceManager.create(JOB_ID)).thenReturn(Path.of("ws"));
        when(dockerExecutor.runJobCommands(anyString(), any(), any(), anyString(), any(),
                any(), any())).thenReturn(new DockerExecutor.CommandResult(-1, List.of(), true, "timeout"));

        runner.poll();

        ArgumentCaptor<JobResult> captor = ArgumentCaptor.forClass(JobResult.class);
        verify(apiClient).reportResult(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(JobStatus.FAILED);
    }

    @Test
    void pollHandlesExecutionExceptionAndCleansUp() throws Exception {
        runner.register();
        when(apiClient.nextJob()).thenReturn(Optional.of(claim()));
        when(workspaceManager.create(JOB_ID)).thenReturn(Path.of("ws"));
        doThrow(new IOException("clone failed"))
                .when(gitCheckout).cloneAndCheckout(anyString(), anyString(), any());

        runner.poll();

        ArgumentCaptor<JobResult> captor = ArgumentCaptor.forClass(JobResult.class);
        verify(apiClient).reportResult(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(JobStatus.FAILED);
        verify(workspaceManager).cleanup(JOB_ID);
    }

    @Test
    void heartbeatReportsOnlineAndBusy() {
        runner.register();
        runner.heartbeat();
        verify(apiClient).heartbeat(eq(RunnerStatus.ONLINE), any());
    }
}
