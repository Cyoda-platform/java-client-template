package com.java_template.application.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * DTO for workflow engine execution options.
 * 
 * <p>Controls how the workflow engine processes transitions and executes processors.
 * These options allow for simulation mode (dry-run) and limiting execution steps
 * to prevent infinite loops or runaway workflows.</p>
 * 
 * <p>Example usage in request body:</p>
 * <pre>
 * {
 *   "engineOptions": {
 *     "simulate": false,
 *     "maxSteps": 50
 *   }
 * }
 * </pre>
 * 
 * <p><strong>NOTE:</strong> Engine options integration with Cyoda workflow engine
 * is not yet implemented. These options are accepted in the API but not currently
 * passed to the underlying workflow execution. Future implementation will require
 * updates to EntityService and CyodaRepository.</p>
 * 
 * @see TransitionRequest
 */
@Data
public class EngineOptions {
    
    /**
     * If true, runs the workflow in simulation mode (dry-run).
     * No actual state changes or side effects are persisted.
     * Useful for validating workflow logic before committing changes.
     * 
     * Default: false
     */
    @JsonProperty("simulate")
    private Boolean simulate = false;
    
    /**
     * Maximum number of workflow steps to execute.
     * Prevents infinite loops in workflow execution.
     * The engine will stop after this many transitions/processor executions.
     * 
     * Default: 50
     */
    @JsonProperty("maxSteps")
    private Integer maxSteps = 50;
}

