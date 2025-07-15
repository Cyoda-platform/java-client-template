package com.java_template.common.workflow;


import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;

/**
 * Interface for criteria checking components.

 * IMPORTANT: CyodaCriterion implementations should be PURE FUNCTIONS:
 * - They should NOT modify the input payload
 * - They should NOT have side effects
 * - They should only evaluate conditions and return boolean results
 * - Any entity modifications should be done by CyodaProcessor implementations instead

 * This ensures criteria checking is predictable, testable, and cacheable.

 * CyodaCriterion components handle EntityCriteriaCalculationRequest events
 * from the CyodaCalculationMemberClient.
 */
public interface CyodaCriterion {

    /**
     * Evaluates criteria against the given EntityCriteriaCalculationRequest.
     * The criteria checker can decide internally how to handle the request:
     * - Use serializers/marshallers to convert to ObjectNode or entity types
     * - Work directly with the request object
     * - Use adapters for data conversion

     * IMPORTANT: This method MUST NOT modify the request payload. It should be a pure function
     * that only reads from the request and returns the evaluation result.

     * This gives criteria checkers complete control over data marshalling and evaluation approach.
     *
     * @param request the EntityCriteriaCalculationRequest to evaluate (MUST NOT be modified)
     * @return the EntityCriteriaCalculationResponse with evaluation result
     */
    EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> request);

    /**
     * Checks if this criteria checker supports the given model key.
     * Used to filter criteria checkers based on entity operationName and version
     * from event metadata before selecting by criteria operationName.
     *
     * @param opsSpec the model key containing entity operationName and version
     * @return true if this criteria checker supports the given model key, false otherwise
     */
    boolean supports(OperationSpecification opsSpec);


}
