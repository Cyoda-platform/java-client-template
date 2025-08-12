package com.java_template.prototype.workflow.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Represents a state in a workflow definition.
 * Contains transitions that can be taken from this state.
 */
public class State {
    
    @JsonProperty("transitions")
    private List<Transition> transitions;
    
    // Default constructor for Jackson
    public State() {}
    
    public List<Transition> getTransitions() {
        return transitions;
    }
    
    public void setTransitions(List<Transition> transitions) {
        this.transitions = transitions;
    }
    
    /**
     * Finds a transition by name.
     * 
     * @param transitionName The name of the transition
     * @return The transition, or null if not found
     */
    public Transition getTransition(String transitionName) {
        if (transitions == null) {
            return null;
        }
        
        return transitions.stream()
                .filter(t -> transitionName.equals(t.getName()))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * Gets the first available transition (for states with only one transition).
     * 
     * @return The first transition, or null if no transitions exist
     */
    public Transition getFirstTransition() {
        return transitions != null && !transitions.isEmpty() ? transitions.get(0) : null;
    }
    
    /**
     * Checks if this state has any transitions.
     * 
     * @return true if transitions exist, false otherwise
     */
    public boolean hasTransitions() {
        return transitions != null && !transitions.isEmpty();
    }
    
    /**
     * Gets the number of transitions from this state.
     * 
     * @return The number of transitions
     */
    public int getTransitionCount() {
        return transitions != null ? transitions.size() : 0;
    }
    
    @Override
    public String toString() {
        return "State{" +
                "transitionsCount=" + (transitions != null ? transitions.size() : 0) +
                '}';
    }
}
