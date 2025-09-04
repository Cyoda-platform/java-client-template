package com.java_template.common.workflow;


import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;

/**
 * ABOUTME: Interface for criteria checking components that evaluate conditions
 * as pure functions without side effects in the workflow execution framework.

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
     * Checks if this criterion supports the given operation specification.
     * Used by OperationFactory to match criteria to workflow operations based on operation name.
     * Implementations typically check if opsSpec.operationName() matches the criterion's expected operation name.
     * Some implementations may also need to check opsSpec.modelKey().getName() and opsSpec.modelKey().getVersion()
     * when using CriterionSerializer.extractEntityWithMetadata() approach to ensure compatibility with specific entity types and versions.
     *
     * @param opsSpec the operation specification containing the operation name from workflow configuration
     *                and model specification with entity name and version
     * @return true if this criterion supports the given operation specification, false otherwise
     */
    boolean supports(OperationSpecification opsSpec);


}
