package com.forgeci.validator;

import com.forgeci.model.JobDefinition;
import com.forgeci.model.PipelineDefinition;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PipelineValidator {

    private PipelineValidator() {}

    public static List<String> validate(PipelineDefinition definition) {
        List<String> errors = new ArrayList<>();
        if (definition == null) {
            errors.add("Pipeline definition cannot be null");
            return errors;
        }

        if (definition.getJobs() == null || definition.getJobs().isEmpty()) {
            errors.add("Pipeline must contain at least one job");
            return errors;
        }

        if (definition.getName() != null && definition.getName().isBlank()) {
            errors.add("Pipeline name cannot be blank");
        }

        Map<String, JobDefinition> jobs = definition.getJobs();
        Set<String> jobNames = new HashSet<>(jobs.keySet());

        for (Map.Entry<String, JobDefinition> entry : jobs.entrySet()) {
            String name = entry.getKey();
            if (name == null || name.isBlank()) {
                errors.add("Job name cannot be blank");
                continue;
            }
            JobDefinition job = entry.getValue();
            if (job == null) {
                errors.add("Job '" + name + "' must not be null");
                continue;
            }

            List<String> commands = job.getCommands();
            if (commands == null || commands.isEmpty()) {
                errors.add("Job '" + name + "' has no commands");
            } else {
                for (String cmd : commands) {
                    if (cmd == null || cmd.isBlank()) {
                        errors.add("Job '" + name + "' contains an empty command");
                    }
                }
            }

            Integer timeout = job.getTimeout();
            if (timeout != null && timeout <= 0) {
                errors.add("Job '" + name + "' has invalid timeout: " + timeout);
            }

            List<String> needs = job.getNeeds();
            if (needs != null) {
                for (String need : needs) {
                    if (!jobNames.contains(need)) {
                        errors.add("Job '" + name + "' depends on unknown job '" + need + "'");
                    }
                    if (need.equals(name)) {
                        errors.add("Job '" + name + "' depends on itself");
                    }
                }
            }
        }

        if (hasCycle(jobs)) {
            errors.add("Pipeline contains a circular dependency");
        }
        return errors;
    }

    private static boolean hasCycle(Map<String, JobDefinition> jobs) {
        Map<String, Integer> state = new LinkedHashMap<>();
        for (String name : jobs.keySet()) {
            state.put(name, 0);
        }
        for (String name : jobs.keySet()) {
            if (dfs(name, jobs, state)) {
                return true;
            }
        }
        return false;
    }

    private static boolean dfs(String jobName, Map<String, JobDefinition> jobs, Map<String, Integer> state) {
        int s = state.getOrDefault(jobName, 0);
        if (s == 1) {
            return true;
        }
        if (s == 2) {
            return false;
        }
        state.put(jobName, 1);
        JobDefinition job = jobs.get(jobName);
        if (job != null && job.getNeeds() != null) {
            for (String need : job.getNeeds()) {
                if (jobs.containsKey(need) && dfs(need, jobs, state)) {
                    return true;
                }
            }
        }
        state.put(jobName, 2);
        return false;
    }
}