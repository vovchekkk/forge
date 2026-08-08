package com.forgeci.server.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * A directed acyclic graph of jobs.
 */
public final class Dag {

    private final Map<String, Set<String>> needsByJob;
    private final Map<String, Set<String>> dependentsByJob;

    private Dag(Map<String, Set<String>> needsByJob, Map<String, Set<String>> dependentsByJob) {
        this.needsByJob = needsByJob;
        this.dependentsByJob = dependentsByJob;
    }

    public static Dag of(Map<String, ? extends Collection<String>> jobNeeds) {
        Map<String, Set<String>> needs = new HashMap<>();
        Map<String, Set<String>> dependents = new HashMap<>();
        for (String job : jobNeeds.keySet()) {
            needs.put(job, new LinkedHashSet<>());
            dependents.put(job, new LinkedHashSet<>());
        }
        for (Map.Entry<String, ? extends Collection<String>> entry : jobNeeds.entrySet()) {
            String job = entry.getKey();
            if (entry.getValue() == null) {
                continue;
            }
            for (String need : entry.getValue()) {
                if (needs.containsKey(need)) {
                    needs.get(job).add(need);
                    dependents.get(need).add(job);
                }
            }
        }
        return new Dag(needs, dependents);
    }

    public Set<String> jobs() {
        return Collections.unmodifiableSet(needsByJob.keySet());
    }

    public Set<String> needsOf(String job) {
        return needsByJob.getOrDefault(job, Collections.emptySet());
    }

    public Set<String> dependentsOf(String job) {
        return dependentsByJob.getOrDefault(job, Collections.emptySet());
    }

    /** Jobs with no needs - candidates to run first. */
    public List<String> rootJobs() {
        List<String> roots = new ArrayList<>();
        for (String job : needsByJob.keySet()) {
            if (needsByJob.get(job).isEmpty()) {
                roots.add(job);
            }
        }
        return roots;
    }

    /** Detect a cycle. Returns true when a cycle exists. */
    public boolean hasCycle() {
        return findCycle() != null;
    }

    /** Return one cycle path if present, else null. */
    public List<String> findCycle() {
        Set<String> visited = new HashSet<>();
        Set<String> stack = new HashSet<>();
        for (String job : needsByJob.keySet()) {
            List<String> cycle = dfs(job, visited, stack, new ArrayList<>());
            if (cycle != null) {
                return cycle;
            }
        }
        return null;
    }

    private List<String> dfs(String job, Set<String> visited, Set<String> stack, List<String> path) {
        if (stack.contains(job)) {
            List<String> cycle = new ArrayList<>(path.subList(path.indexOf(job), path.size()));
            cycle.add(job);
            return cycle;
        }
        if (visited.contains(job)) {
            return null;
        }
        visited.add(job);
        stack.add(job);
        path.add(job);
        for (String need : needsByJob.getOrDefault(job, Set.of())) {
            List<String> cycle = dfs(need, visited, stack, path);
            if (cycle != null) {
                return cycle;
            }
        }
        path.remove(path.size() - 1);
        stack.remove(job);
        return null;
    }

    /**
     * Compute jobs whose dependencies have all succeeded.
     * @param succeeded the set of job names already SUCCESS
     * @param skipRules job name -> skip policy (or null when not applicable)
     */
    public List<String> nextReady(Set<String> succeeded, Function<String, Boolean> skipRules) {
        List<String> ready = new ArrayList<>();
        for (String job : needsByJob.keySet()) {
            if (succeeded.contains(job)) {
                continue;
            }
            if (Boolean.TRUE.equals(skipRules.apply(job))) {
                continue;
            }
            boolean allMet = true;
            for (String need : needsByJob.get(job)) {
                if (!succeeded.contains(need)) {
                    allMet = false;
                    break;
                }
            }
            if (allMet) {
                ready.add(job);
            }
        }
        return ready;
    }
}