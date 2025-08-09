package com.java_template.prototype.workflow.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Represents processor configuration in a workflow transition.
 * Maps to processor definitions in workflow JSON files.
 */
public class ProcessorConfig {
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("executionMode")
    private String executionMode;
    
    @JsonProperty("config")
    private Map<String, Object> config;
    
    // Default constructor for Jackson
    public ProcessorConfig() {}
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getExecutionMode() {
        return executionMode;
    }
    
    public void setExecutionMode(String executionMode) {
        this.executionMode = executionMode;
    }
    
    public Map<String, Object> getConfig() {
        return config;
    }
    
    public void setConfig(Map<String, Object> config) {
        this.config = config;
    }
    
    /**
     * Gets a configuration value by key.
     * 
     * @param key The configuration key
     * @return The configuration value, or null if not found
     */
    public Object getConfigValue(String key) {
        return config != null ? config.get(key) : null;
    }
    
    /**
     * Gets a configuration value as a string.
     * 
     * @param key The configuration key
     * @return The configuration value as string, or null if not found
     */
    public String getConfigString(String key) {
        Object value = getConfigValue(key);
        return value != null ? value.toString() : null;
    }
    
    @Override
    public String toString() {
        return "ProcessorConfig{" +
                "name='" + name + '\'' +
                ", executionMode='" + executionMode + '\'' +
                ", configSize=" + (config != null ? config.size() : 0) +
                '}';
    }
}
