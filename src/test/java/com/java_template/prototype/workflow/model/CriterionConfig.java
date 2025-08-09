package com.java_template.prototype.workflow.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents criterion configuration in a workflow transition.
 * Maps to criterion definitions in workflow JSON files.
 */
public class CriterionConfig {
    
    @JsonProperty("type")
    private String type;
    
    @JsonProperty("function")
    private FunctionConfig function;
    
    // Default constructor for Jackson
    public CriterionConfig() {}
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public FunctionConfig getFunction() {
        return function;
    }
    
    public void setFunction(FunctionConfig function) {
        this.function = function;
    }
    
    /**
     * Gets the criterion function name.
     * 
     * @return The function name, or null if no function is configured
     */
    public String getFunctionName() {
        return function != null ? function.getName() : null;
    }
    
    @Override
    public String toString() {
        return "CriterionConfig{" +
                "type='" + type + '\'' +
                ", functionName='" + getFunctionName() + '\'' +
                '}';
    }
}
