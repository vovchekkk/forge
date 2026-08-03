package com.forgeci.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.forgeci.model.JobDefinition;
import com.forgeci.model.PipelineDefinition;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class PipelineParserTest {

    @Test
    void parsesValidConfig() throws IOException {
        String yaml = """
                name: Java CI
                image: eclipse-temurin:25-jdk
                jobs:
                  test:
                    commands:
                      - ./mvnw test
                  build:
                    needs: [test]
                    commands:
                      - ./mvnw package
                """;
        PipelineDefinition def = PipelineParser.parse(yaml);
        assertEquals("Java CI", def.getName());
        assertEquals("eclipse-temurin:25-jdk", def.getImage());
        assertEquals(2, def.getJobs().size());
        assertEquals(List.of("./mvnw test"), def.getJobs().get("test").getCommands());
        assertEquals(List.of("test"), def.getJobs().get("build").getNeeds());
    }

    @Test
    void parsesEnvironmentAndTimeout() throws IOException {
        String yaml = """
                image: alpine
                jobs:
                  test:
                    timeout: 120
                    environment:
                      FOO: bar
                    commands:
                      - echo $FOO
                """;
        PipelineDefinition def = PipelineParser.parse(yaml);
        JobDefinition job = def.getJobs().get("test");
        assertEquals(120, job.getTimeout());
        assertEquals("bar", job.getEnvironment().get("FOO"));
    }

    @Test
    void rejectsMalformedYaml() {
        assertThrows(IOException.class, () -> PipelineParser.parse("jobs: [unclosed"));
    }

    @Test
    void rejectsEmptyConfig() {
        assertThrows(IOException.class, () -> PipelineParser.parse("   "));
    }

    @Test
    void resolvesDefaultImage() throws IOException {
        String yaml = "jobs:\n  a:\n    commands: [echo hi]\n";
        PipelineDefinition def = PipelineParser.parse(yaml);
        assertTrue(def.getImage() == null);
        assertEquals("eclipse-temurin:25-jdk", def.resolvedImage());
    }
}