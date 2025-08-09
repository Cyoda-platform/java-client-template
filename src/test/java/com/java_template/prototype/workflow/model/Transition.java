package com.java_template.prototype.workflow.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Represents a transition between states in a workflow.
 * Contains processors to execute and criteria to evaluate.
 */
public class Transition {
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("next")
    private String nextState;
    
    @JsonProperty("manual")
    private boolean manual;
    
    @JsonProperty("processors")
    private List<ProcessorConfig> processors;
    
    @JsonProperty("criterion")
    private CriterionConfig criterion;
    
    // Default constructor for Jackson
    public Transition() {}
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getNextState() {
        return nextState;
    }
    
    public void setNextState(String nextState) {
        this.nextState = nextState;
    }
    
    public boolean isManual() {
        return manual;
    }
    
    public void setManual(boolean manual) {
        this.manual = manual;
    }
    
    public List<ProcessorConfig> getProcessors() {
        return processors;
    }
    
    public void setProcessors(List<ProcessorConfig> processors) {
        this.processors = processors;
    }
    
    public CriterionConfig getCriterion() {
        return criterion;
    }
    
    public void setCriterion(CriterionConfig criterion) {
        this.criterion = criterion;
    }
    
    /**
     * Checks if this transition has processors to execute.
     * 
     * @return true if processors exist, false otherwise
     */
    public boolean hasProcessors() {
        return processors != null && !processors.isEmpty();
    }
    
    /**
     * Checks if this transition has a criterion to evaluate.
     * 
     * @return true if criterion exists, false otherwise
     */
    public boolean hasCriterion() {
        return criterion != null;
    }
    
    @Override
    public String toString() {
        return "Transition{" +
                "name='" + name + '\'' +
                ", nextState='" + nextState + '\'' +
                ", manual=" + manual +
                ", hasProcessors=" + hasProcessors() +
                ", hasCriterion=" + hasCriterion() +
                '}';
    }
}
