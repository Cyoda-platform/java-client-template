package com.java_template.common.workflow;

import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

/**
 * ABOUTME: Interface for Cyoda workflow processors that handle entity transformation
 * and business logic processing within the workflow execution framework.
 * Each workflow method should be implemented as a separate processor class.
 * Processors handle ObjectNode payload and decide internally how to process it,
 * allowing flexibility in entity handling and conversion strategies.

 * CyodaProcessor components handle EntityProcessorCalculationRequest events
 * from the CyodaCalculationMemberClient and are responsible for entity transformation
 * and business logic processing.
 */
public interface CyodaProcessor {

    /**
     * Processes the given EntityProcessorCalculationRequest.
     * The processor can decide internally how to handle the request:
     * - Use serializers/marshallers to convert to ObjectNode or entity types
     * - Work directly with the request object
     * - Use adapters for data conversion

     * This gives processors complete control over data marshalling and processing approach.
     *
     * @param context the CyodaEventContext to process
     * @return the EntityProcessorCalculationResponse
     */
    EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context);

    /**
     * Checks if this processor supports the given operation specification.
     * Used by OperationFactory to match processors to workflow operations based on operation name.
     * Implementations typically check if opSpec.operationName() matches the processor's expected operation name.
     * Some implementations may also need to check opSpec.modelKey().getName() and opSpec.modelKey().getVersion()
     * when using ProcessorSerializer.toEntityWithMetadata() approach to ensure compatibility with specific entity types and versions.
     *
     * @param opSpec the operation specification containing the operation name from workflow configuration
     *               and model specification with entity name and version
     * @return true if this processor supports the given operation specification, false otherwise
     */
    boolean supports(OperationSpecification opSpec);

}
