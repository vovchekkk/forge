package com.forgeci.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PipelineDefinition {
    private String name;
    private String image;
    private Map<String, JobDefinition> jobs;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }

    public Map<String, JobDefinition> getJobs() { return jobs; }
    public void setJobs(Map<String, JobDefinition> jobs) { this.jobs = jobs; }

    public String resolvedImage() {
        return (image == null || image.isBlank()) ? "eclipse-temurin:25-jdk" : image;
    }
}