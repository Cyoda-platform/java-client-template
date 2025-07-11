package com.java_template.application.serializer;

import com.fasterxml.jackson.databind.JsonNode;
import com.java_template.common.workflow.CyodaEntity;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;

/**
 * Criterion serializer interface that integrates with sealed response builders.
 * Provides type-safe response building with sealed interface hierarchy.
 */
public interface CriterionSerializer {

    /**
     * Extracts a typed entity from the request payload.
     */
    <T extends CyodaEntity> T extractEntity(EntityCriteriaCalculationRequest request, Class<T> clazz);

    /**
     * Extracts raw JSON payload from the request.
     */
    JsonNode extractPayload(EntityCriteriaCalculationRequest request);

    /**
     * Converts a CyodaEntity to JsonNode.
     * This method allows criteria to convert entities for validation purposes.
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
    default <R> R executeFunction(EntityCriteriaCalculationRequest request,
                                  java.util.function.Function<CriterionSerializer, R> function) {
        return function.apply(this);
    }

    /**
     * Creates a match-focused response builder for the given request.
     * This builder is optimized for match result construction.
     */
    ResponseBuilder.CriterionBuilder matchResponse(EntityCriteriaCalculationRequest request);

    /**
     * Creates an error-focused response builder for the given request.
     * This builder is optimized for error response construction.
     */
    ResponseBuilder.CriterionBuilder errorResponse(EntityCriteriaCalculationRequest request);

}
