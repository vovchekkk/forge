package com.forgeci.server.domain;

import com.forgeci.model.JobStatus;
import com.forgeci.model.PipelineRunStatus;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class StateMachine {

    private static final Map<PipelineRunStatus, Set<PipelineRunStatus>> RUN_TRANSITIONS = new EnumMap<>(PipelineRunStatus.class);
    private static final Map<JobStatus, Set<JobStatus>> JOB_TRANSITIONS = new EnumMap<>(JobStatus.class);

    static {
        RUN_TRANSITIONS.put(PipelineRunStatus.CREATED, EnumSet.of(PipelineRunStatus.QUEUED, PipelineRunStatus.CANCELED));
        RUN_TRANSITIONS.put(PipelineRunStatus.QUEUED, EnumSet.of(PipelineRunStatus.RUNNING, PipelineRunStatus.SUCCESS, PipelineRunStatus.FAILED, PipelineRunStatus.CANCELED));
        RUN_TRANSITIONS.put(PipelineRunStatus.RUNNING, EnumSet.of(PipelineRunStatus.SUCCESS, PipelineRunStatus.FAILED, PipelineRunStatus.CANCELED));
        RUN_TRANSITIONS.put(PipelineRunStatus.SUCCESS, EnumSet.noneOf(PipelineRunStatus.class));
        RUN_TRANSITIONS.put(PipelineRunStatus.FAILED, EnumSet.noneOf(PipelineRunStatus.class));
        RUN_TRANSITIONS.put(PipelineRunStatus.CANCELED, EnumSet.noneOf(PipelineRunStatus.class));
    }

    static {
        JOB_TRANSITIONS.put(JobStatus.PENDING, EnumSet.of(JobStatus.READY, JobStatus.SKIPPED, JobStatus.CANCELED));
        JOB_TRANSITIONS.put(JobStatus.READY, EnumSet.of(JobStatus.QUEUED, JobStatus.RUNNING, JobStatus.SKIPPED, JobStatus.CANCELED));
        JOB_TRANSITIONS.put(JobStatus.QUEUED, EnumSet.of(JobStatus.RUNNING, JobStatus.SUCCESS, JobStatus.FAILED, JobStatus.CANCELED));
        JOB_TRANSITIONS.put(JobStatus.RUNNING, EnumSet.of(JobStatus.SUCCESS, JobStatus.FAILED, JobStatus.CANCELED));
        JOB_TRANSITIONS.put(JobStatus.SUCCESS, EnumSet.noneOf(JobStatus.class));
        JOB_TRANSITIONS.put(JobStatus.FAILED, EnumSet.noneOf(JobStatus.class));
        JOB_TRANSITIONS.put(JobStatus.CANCELED, EnumSet.noneOf(JobStatus.class));
        JOB_TRANSITIONS.put(JobStatus.SKIPPED, EnumSet.noneOf(JobStatus.class));
    }

    private StateMachine() {}

    public static boolean canTransition(PipelineRunStatus from, PipelineRunStatus to) {
        return RUN_TRANSITIONS.getOrDefault(from, EnumSet.noneOf(PipelineRunStatus.class)).contains(to);
    }

    public static boolean canTransition(JobStatus from, JobStatus to) {
        return JOB_TRANSITIONS.getOrDefault(from, EnumSet.noneOf(JobStatus.class)).contains(to);
    }

    public static void ensureRunTransition(PipelineRunStatus from, PipelineRunStatus to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException("Illegal pipeline run transition: " + from + " -> " + to);
        }
    }

    public static void ensureJobTransition(JobStatus from, JobStatus to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException("Illegal job transition: " + from + " -> " + to);
        }
    }
}