package com.java_template.prototype.workflow.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Represents function configuration within a criterion.
 * Maps to function definitions in workflow JSON criterion sections.
 */
public class FunctionConfig {
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("config")
    private Map<String, Object> config;
    
    // Default constructor for Jackson
    public FunctionConfig() {}
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
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
        return "FunctionConfig{" +
                "name='" + name + '\'' +
                ", configSize=" + (config != null ? config.size() : 0) +
                '}';
    }
}
