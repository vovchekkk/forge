package com.forgeci.server.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DagTest {

    @Test
    void rootJobsAreIndependent() {
        Dag dag = Dag.of(Map.of(
                "a", List.of(),
                "b", List.of(),
                "c", List.of("a", "b")));
        assertEquals(Set.of("a", "b"), Set.copyOf(dag.rootJobs()));
        assertTrue(dag.needsOf("c").containsAll(List.of("a", "b")));
        assertTrue(dag.dependentsOf("a").contains("c"));
    }

    @Test
    void detectsCycle() {
        Dag dag = Dag.of(Map.of(
                "a", List.of("b"),
                "b", List.of("a")));
        assertTrue(dag.hasCycle());
        assertFalse(dag.findCycle().isEmpty());
    }

    @Test
    void noCycle() {
        Dag dag = Dag.of(Map.of(
                "a", List.of(),
                "b", List.of("a"),
                "c", List.of("b")));
        assertFalse(dag.hasCycle());
    }

    @Test
    void nextReadyUnlocksAfterDependencySucceeds() {
        Dag dag = Dag.of(Map.of(
                "a", List.of(),
                "b", List.of("a"),
                "c", List.of("a")));

        // Nothing succeeded -> only a is ready
        assertEquals(List.of("a"), dag.nextReady(Set.of(), j -> false));

        // After a succeeds, b and c become ready
        assertEquals(Set.of("b", "c"), Set.copyOf(dag.nextReady(Set.of("a"), j -> false)));
    }

    @Test
    void skippedJobsAreExcluded() {
        Dag dag = Dag.of(Map.of(
                "a", List.of(),
                "b", List.of("a"),
                "c", List.of("a")));
        // b is skipped
        assertEquals(List.of("a"), dag.nextReady(Set.of(), j -> "b".equals(j)));
    }
}