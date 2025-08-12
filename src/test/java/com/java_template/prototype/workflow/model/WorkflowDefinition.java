package com.java_template.prototype.workflow.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Represents a complete workflow definition loaded from JSON.
 * Maps to the structure of workflow JSON files in src/main/resources/workflow/
 */
public class WorkflowDefinition {
    
    @JsonProperty("version")
    private String version;
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("desc")
    private String description;
    
    @JsonProperty("initialState")
    private String initialState;
    
    @JsonProperty("active")
    private boolean active;
    
    @JsonProperty("states")
    private Map<String, State> states;
    
    // Default constructor for Jackson
    public WorkflowDefinition() {}
    
    public String getVersion() {
        return version;
    }
    
    public void setVersion(String version) {
        this.version = version;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getInitialState() {
        return initialState;
    }
    
    public void setInitialState(String initialState) {
        this.initialState = initialState;
    }
    
    public boolean isActive() {
        return active;
    }
    
    public void setActive(boolean active) {
        this.active = active;
    }
    
    public Map<String, State> getStates() {
        return states;
    }
    
    public void setStates(Map<String, State> states) {
        this.states = states;
    }
    
    /**
     * Gets a state by name.
     * 
     * @param stateName The name of the state
     * @return The state, or null if not found
     */
    public State getState(String stateName) {
        return states != null ? states.get(stateName) : null;
    }
    
    /**
     * Checks if this workflow definition is valid.
     * 
     * @return true if valid, false otherwise
     */
    public boolean isValid() {
        return active && 
               initialState != null && 
               states != null && 
               states.containsKey(initialState);
    }
    
    @Override
    public String toString() {
        return "WorkflowDefinition{" +
                "name='" + name + '\'' +
                ", version='" + version + '\'' +
                ", initialState='" + initialState + '\'' +
                ", active=" + active +
                ", statesCount=" + (states != null ? states.size() : 0) +
                '}';
    }
}
