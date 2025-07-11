package com.java_template.application.serializer_deprecated;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.cyoda.cloud.api.event.common.Error;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

/**
 * Serializer/Marshaller for EntityCriteriaCalculationRequest/Response.
 * <p>
 * Provides flexible data extraction and response creation for criteria checkers:
 * <p>
 * EXTRACTION OPTIONS:
 * - extractEntity(request, Class<T>) - Type-safe entity extraction with explicit class (inherited)
 * - extractPayload(request) - Raw ObjectNode for custom processing (inherited)
 * <p>
 * RESPONSE CREATION:
 * - createSuccessResponse(request, boolean) - Standard success response
 * - createErrorResponse(request, String) - Standard error response (inherited)
 * <p>
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
    public ObjectNode extractPayload(@NotNull EntityCriteriaCalculationRequest request) {
        validateRequest(request);
        return (ObjectNode) request.getPayload().getData();
    }

    @Override
    protected void validateRequest(@NotNull EntityCriteriaCalculationRequest request) {
        if (request.getPayload() == null) {
            throw new IllegalArgumentException("Request payload cannot be null");
        }
        if (request.getPayload().getData() == null) {
            throw new IllegalArgumentException("Request payload data cannot be null");
        }
    }

    @Override
    public EntityCriteriaCalculationResponse createResponse(@NotNull EntityCriteriaCalculationRequest request) {

        EntityCriteriaCalculationResponse response = new EntityCriteriaCalculationResponse();
        response.setId(request.getId());
        // Note: EntityCriteriaCalculationResponse doesn't have setPayload method
        // The payload is not needed in the response for criteria checking
        response.setMatches(true);
        response.setEntityId(request.getEntityId());
        response.setRequestId(request.getId());
        return response;
    }

    @Override
    public EntityCriteriaCalculationResponse createErrorResponse(
            @NotNull EntityCriteriaCalculationRequest request,
            @NotNull String errorCode,
            @NotNull String errorMessage
    ) {
        logger.error("Criteria evaluation failed: {}", errorMessage);

        EntityCriteriaCalculationResponse response = createResponse(request);
        response.setSuccess(false);
        response.setMatches(false);
        org.cyoda.cloud.api.event.common.Error error = new Error();
        error.setCode(errorCode);
        error.setMessage(errorMessage);
        response.setError(error);
        return response;
    }

    // ========================================
    // CRITERIA-SPECIFIC METHODS
    // ========================================

    /**
     * Creates a successful response with the evaluation result.
     *
     * @param request the original request
     * @param matches whether the criteria was met
     * @return EntityCriteriaCalculationResponse indicating success and result
     */
    public EntityCriteriaCalculationResponse createSuccessResponse(@NotNull EntityCriteriaCalculationRequest request, boolean matches) {
        EntityCriteriaCalculationResponse response = createResponse(request);
        response.setSuccess(true);
        response.setMatches(matches);
        return response;
    }
}
