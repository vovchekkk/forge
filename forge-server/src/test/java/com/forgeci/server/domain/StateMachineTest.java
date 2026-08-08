package com.forgeci.server.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.forgeci.model.JobStatus;
import com.forgeci.model.PipelineRunStatus;
import org.junit.jupiter.api.Test;

class StateMachineTest {

    @Test
    void pipelineRunValidTransitions() {
        assertTrue(StateMachine.canTransition(PipelineRunStatus.CREATED, PipelineRunStatus.QUEUED));
        assertTrue(StateMachine.canTransition(PipelineRunStatus.QUEUED, PipelineRunStatus.RUNNING));
        assertTrue(StateMachine.canTransition(PipelineRunStatus.RUNNING, PipelineRunStatus.SUCCESS));
        assertTrue(StateMachine.canTransition(PipelineRunStatus.RUNNING, PipelineRunStatus.FAILED));
        assertTrue(StateMachine.canTransition(PipelineRunStatus.RUNNING, PipelineRunStatus.CANCELED));
        assertTrue(StateMachine.canTransition(PipelineRunStatus.QUEUED, PipelineRunStatus.CANCELED));
    }

    @Test
    void pipelineRunTerminalStatesAreFinal() {
        assertFalse(StateMachine.canTransition(PipelineRunStatus.SUCCESS, PipelineRunStatus.RUNNING));
        assertFalse(StateMachine.canTransition(PipelineRunStatus.FAILED, PipelineRunStatus.SUCCESS));
        assertFalse(StateMachine.canTransition(PipelineRunStatus.CANCELED, PipelineRunStatus.QUEUED));
        assertFalse(StateMachine.canTransition(PipelineRunStatus.CREATED, PipelineRunStatus.SUCCESS));
    }

    @Test
    void jobValidTransitions() {
        assertTrue(StateMachine.canTransition(JobStatus.PENDING, JobStatus.READY));
        assertTrue(StateMachine.canTransition(JobStatus.PENDING, JobStatus.SKIPPED));
        assertTrue(StateMachine.canTransition(JobStatus.READY, JobStatus.QUEUED));
        assertTrue(StateMachine.canTransition(JobStatus.READY, JobStatus.RUNNING));
        assertTrue(StateMachine.canTransition(JobStatus.RUNNING, JobStatus.SUCCESS));
        assertTrue(StateMachine.canTransition(JobStatus.RUNNING, JobStatus.FAILED));
        assertTrue(StateMachine.canTransition(JobStatus.RUNNING, JobStatus.CANCELED));
        assertTrue(StateMachine.canTransition(JobStatus.READY, JobStatus.SKIPPED));
        assertTrue(StateMachine.canTransition(JobStatus.PENDING, JobStatus.CANCELED));
        assertTrue(StateMachine.canTransition(JobStatus.READY, JobStatus.CANCELED));
    }

    @Test
    void jobTerminalStatesAreFinal() {
        assertFalse(StateMachine.canTransition(JobStatus.SUCCESS, JobStatus.RUNNING));
        assertFalse(StateMachine.canTransition(JobStatus.FAILED, JobStatus.SUCCESS));
        assertFalse(StateMachine.canTransition(JobStatus.SKIPPED, JobStatus.READY));
        assertFalse(StateMachine.canTransition(JobStatus.CANCELED, JobStatus.SUCCESS));
    }

    @Test
    void ensureThrowsOnIllegalTransition() {
        assertThrows(IllegalStateException.class,
                () -> StateMachine.ensureRunTransition(PipelineRunStatus.SUCCESS, PipelineRunStatus.RUNNING));
        assertThrows(IllegalStateException.class,
                () -> StateMachine.ensureJobTransition(JobStatus.SKIPPED, JobStatus.READY));
    }
}