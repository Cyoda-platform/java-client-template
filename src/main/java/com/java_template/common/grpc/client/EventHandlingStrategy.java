package com.java_template.common.grpc.client;

import io.cloudevents.v1.proto.CloudEvent;

import java.util.concurrent.CompletableFuture;

/**
 * Strategy interface for handling different types of CloudEvents.
 * Each implementation handles a specific event type (processors, criteria, etc.).

 * This follows the Strategy Pattern to keep the CyodaCalculationMemberClient
 * clean and extensible - new event types can be added without modifying existing code.

 * Strategies are responsible for processing events and returning results.
 * The CyodaCalculationMemberClient handles sending responses based on the results.
 */
public interface EventHandlingStrategy {

    /**
     * Handles the given CloudEvent and returns the result.
     * Each strategy implementation contains the logic for processing
     * its specific event type and returns the response object.

     * The CyodaCalculationMemberClient is responsible for sending the response.
     *
     * @param cloudEvent the CloudEvent to handle
     * @return CompletableFuture containing the response object to be sent
     */
    CompletableFuture<Object> handleEvent(CloudEvent cloudEvent);

    /**
     * Checks if this strategy supports the given event type.
     *
     * @param eventType the event type to check
     * @return true if this strategy can handle the event type, false otherwise
     */
    boolean supports(String eventType);

    /**
     * Gets the name of this strategy for logging and identification.
     *
     * @return the strategy name
     */
    String getStrategyName();
}
