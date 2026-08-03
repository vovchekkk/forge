package com.forgeci.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JobDefinition {
    private List<String> commands;
    private List<String> needs;
    private Integer timeout;
    private Map<String, String> environment;

    public List<String> getCommands() { return commands; }
    public void setCommands(List<String> commands) { this.commands = commands; }

    public List<String> getNeeds() { return needs; }
    public void setNeeds(List<String> needs) { this.needs = needs; }

    public Integer getTimeout() { return timeout; }
    public void setTimeout(Integer timeout) { this.timeout = timeout; }

    public Map<String, String> getEnvironment() { return environment; }
    public void setEnvironment(Map<String, String> environment) { this.environment = environment; }
}