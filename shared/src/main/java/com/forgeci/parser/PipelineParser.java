package com.forgeci.parser;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.forgeci.model.PipelineDefinition;
import java.io.IOException;
import java.io.InputStream;

public final class PipelineParser {
    private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private PipelineParser() {}

    public static PipelineDefinition parse(String content) throws IOException {
        if (content == null || content.isBlank()) {
            throw new IOException("Pipeline configuration is empty");
        }
        return mapper.readValue(content, PipelineDefinition.class);
    }

    public static PipelineDefinition parse(InputStream inputStream) throws IOException {
        return mapper.readValue(inputStream, PipelineDefinition.class);
    }
}