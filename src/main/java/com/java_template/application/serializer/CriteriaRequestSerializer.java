package com.java_template.application.serializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.cyoda.cloud.api.event.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.EntityCriteriaCalculationResponse;
import org.springframework.stereotype.Component;

/**
 * Serializer/Marshaller for EntityCriteriaCalculationRequest/Response.

 * Provides flexible data extraction and response creation for criteria checkers:

 * EXTRACTION OPTIONS:
 * - extractEntity(request, Class<T>) - Type-safe entity extraction with explicit class (inherited)
 * - extractPayload(request) - Raw ObjectNode for custom processing (inherited)

 * RESPONSE CREATION:
 * - createSuccessResponse(request, boolean) - Standard success response
 * - createErrorResponse(request, String) - Standard error response (inherited)

 * DESIGN PRINCIPLES:
 * - Type safety with generic methods
 * - Graceful error handling with clear exceptions
 * - Consistent response format
 * - Support for both typed and untyped processing
 */
@Component
public class CriteriaRequestSerializer extends BaseRequestSerializer<EntityCriteriaCalculationRequest, EntityCriteriaCalculationResponse> {

    public CriteriaRequestSerializer(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    // ========================================
    // IMPLEMENTATION OF ABSTRACT METHODS
    // ========================================

    @Override
    public ObjectNode extractPayload(EntityCriteriaCalculationRequest request) {
        validateRequest(request);
        return (ObjectNode) request.getPayload().getData();
    }

    @Override
    protected void validateRequest(EntityCriteriaCalculationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("EntityCriteriaCalculationRequest cannot be null");
        }
        if (request.getPayload() == null) {
            throw new IllegalArgumentException("Request payload cannot be null");
        }
        if (request.getPayload().getData() == null) {
            throw new IllegalArgumentException("Request payload data cannot be null");
        }
    }

    @Override
    public EntityCriteriaCalculationResponse createResponse(EntityCriteriaCalculationRequest request) {
        EntityCriteriaCalculationResponse response = new EntityCriteriaCalculationResponse();
        response.setId(request.getId());
        // Note: EntityCriteriaCalculationResponse doesn't have setPayload method
        // The payload is not needed in the response for criteria checking
        return response;
    }

    @Override
    public EntityCriteriaCalculationResponse createErrorResponse(EntityCriteriaCalculationRequest request, String errorMessage) {
        if (errorMessage != null) {
            logger.error("Criteria evaluation failed: {}", errorMessage);
        }

        EntityCriteriaCalculationResponse response = createResponse(request);
        response.setSuccess(false);
        response.setMatches(false);
        return response;
    }

    // ========================================
    // CRITERIA-SPECIFIC METHODS
    // ========================================

    /**
     * Creates a successful response with the evaluation result.
     * @param request the original request
     * @param matches whether the criteria was met
     * @return EntityCriteriaCalculationResponse indicating success and result
     */
    public EntityCriteriaCalculationResponse createSuccessResponse(EntityCriteriaCalculationRequest request, boolean matches) {
        EntityCriteriaCalculationResponse response = createResponse(request);
        response.setSuccess(true);
        response.setMatches(matches);
        return response;
    }


}
