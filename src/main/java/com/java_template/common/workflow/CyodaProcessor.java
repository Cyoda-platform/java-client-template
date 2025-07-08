package com.java_template.common.workflow;

import org.cyoda.cloud.api.event.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.EntityProcessorCalculationResponse;

import java.util.concurrent.CompletableFuture;

/**
 * Interface for Cyoda workflow processors.
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
     * @param request the EntityProcessorCalculationRequest to process
     * @return CompletableFuture containing the EntityProcessorCalculationResponse
     */
    CompletableFuture<EntityProcessorCalculationResponse> process(EntityProcessorCalculationRequest request);

    /**
     * Checks if this processor supports the given model key.
     * Used to filter processors based on entity name and version
     * from event metadata before selecting by processor name.
     *
     * @param modelKey the model key containing entity name and version
     * @return true if this processor supports the given model key, false otherwise
     */
    boolean supports(ModelKey modelKey);

    /**
     * Gets the processor name for identification and logging.
     * This should typically match the Spring bean name.
     *
     * @return the processor name
     */
    String getName();
}
