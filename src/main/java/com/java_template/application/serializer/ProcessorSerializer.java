package com.java_template.application.serializer;

import com.fasterxml.jackson.databind.JsonNode;
import com.java_template.common.workflow.CyodaEntity;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;

/**
 * Processor serializer interface that integrates with sealed response builders.
 * Provides type-safe response building with sealed interface hierarchy.
 */
public interface ProcessorSerializer {

    /**
     * Extracts a typed entity from the request payload.
     */
    <T extends CyodaEntity> T extractEntity(EntityProcessorCalculationRequest request, Class<T> clazz);

    /**
     * Extracts raw JSON payload from the request.
     */
    JsonNode extractPayload(EntityProcessorCalculationRequest request);

    /**
     * Converts a CyodaEntity to JsonNode.
     * This method allows processors to convert entities for use with withEntity method.
     */
    <T extends CyodaEntity> JsonNode entityToJsonNode(T entity);

    /**
     * Gets the serializer type identifier.
     */
    String getType();

    /**
     * Executes a custom function with the serializer and returns the result.
     * This allows for flexible operations without modifying the interface.
     */
    default <R> R executeFunction(EntityProcessorCalculationRequest request,
                                  java.util.function.Function<ProcessorSerializer, R> function) {
        return function.apply(this);
    }

    /**
     * Creates a success-focused response builder for the given request.
     * This builder is optimized for successful response construction.
     */
    ResponseBuilder.ProcessorBuilder successResponse(EntityProcessorCalculationRequest request);

    /**
     * Creates an error-focused response builder for the given request.
     * This builder is optimized for error response construction.
     */
    ResponseBuilder.ProcessorBuilder errorResponse(EntityProcessorCalculationRequest request);

}
