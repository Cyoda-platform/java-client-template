package com.java_template.application.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * DTO for workflow transition requests.
 * 
 * <p>Used in API requests to trigger workflow transitions on entities.
 * The transition name must match a transition defined in the entity's workflow configuration.</p>
 * 
 * <p>Example usage in request body:</p>
 * <pre>
 * {
 *   "transitionRequest": {
 *     "name": "START",
 *     "comment": "Initiating batch run for data correction"
 *   }
 * }
 * </pre>
 * 
 * @see EngineOptions
 */
@Data
public class TransitionRequest {
    
    /**
     * Name of the workflow transition to trigger.
     * Must match a transition defined in the entity's workflow JSON.
     * Examples: "START", "REJECT", "CANCEL", "APPROVE"
     */
    @JsonProperty("name")
    private String name;
    
    /**
     * Optional comment documenting the reason for the transition.
     * Useful for audit trails and operational transparency.
     */
    @JsonProperty("comment")
    private String comment;
}

