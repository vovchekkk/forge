package com.forgeci.validator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.forgeci.model.PipelineDefinition;
import com.forgeci.parser.PipelineParser;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class PipelineValidatorTest {

    private PipelineDefinition parse(String yaml) throws IOException {
        return PipelineParser.parse(yaml);
    }

    @Test
    void validConfigHasNoErrors() throws IOException {
        String yaml = """
                name: Java CI
                image: eclipse-temurin:25-jdk
                jobs:
                  test:
                    commands: [./mvnw test]
                  build:
                    needs: [test]
                    commands: [./mvnw package]
                """;
        assertTrue(PipelineValidator.validate(parse(yaml)).isEmpty());
    }

    @Test
    void missingJobsFails() throws IOException {
        List<String> errors = PipelineValidator.validate(parse("name: x\n"));
        assertEquals(1, errors.size());
        assertEquals("Pipeline must contain at least one job", errors.get(0));
    }

    @Test
    void unknownDependencyFails() throws IOException {
        String yaml = """
                jobs:
                  a:
                    commands: [echo a]
                    needs: [ghost]
                """;
        List<String> errors = PipelineValidator.validate(parse(yaml));
        assertEquals(1, errors.size());
        assertEquals("Job 'a' depends on unknown job 'ghost'", errors.get(0));
    }

    @Test
    void selfDependencyFails() throws IOException {
        String yaml = """
                jobs:
                  a:
                    commands: [echo a]
                    needs: [a]
                """;
        List<String> errors = PipelineValidator.validate(parse(yaml));
        assertTrue(errors.contains("Job 'a' depends on itself"));
    }

    @Test
    void cycleDetected() throws IOException {
        String yaml = """
                jobs:
                  a:
                    commands: [echo a]
                    needs: [b]
                  b:
                    commands: [echo b]
                    needs: [a]
                """;
        List<String> errors = PipelineValidator.validate(parse(yaml));
        assertEquals(1, errors.size());
        assertEquals("Pipeline contains a circular dependency", errors.get(0));
    }

    @Test
    void noCommandsFails() throws IOException {
        String yaml = """
                jobs:
                  a:
                    needs: [b]
                  b:
                    commands: [echo b]
                """;
        List<String> errors = PipelineValidator.validate(parse(yaml));
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).startsWith("Job 'a' has no commands"));
    }

    @Test
    void invalidTimeoutFails() throws IOException {
        String yaml = """
                jobs:
                  a:
                    commands: [echo a]
                    timeout: -5
                """;
        List<String> errors = PipelineValidator.validate(parse(yaml));
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("invalid timeout"));
    }
}