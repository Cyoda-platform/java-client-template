package com.java_template.application.serializer_deprecated;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.util.JsonUtils;
import com.java_template.common.workflow.CyodaEntity;
import org.cyoda.cloud.api.event.common.Error;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

/**
 * Serializer/Marshaller for EntityProcessorCalculationRequest/Response.

 * Provides flexible data extraction and response creation for processors:

 * EXTRACTION OPTIONS:
 * - extractEntity(request, Class<T>) - Type-safe entity extraction with explicit class (inherited)
 * - extractPayload(request) - Raw ObjectNode for custom processing (inherited)

 * RESPONSE CREATION:
 * - createSuccessResponse(request, CyodaEntity) - Response from processed entity
 * - createSuccessResponse(request, ObjectNode) - Response from processed ObjectNode
 * - createErrorResponse(request, String) - Standard error response (inherited)

 * DESIGN PRINCIPLES:
 * - Type safety with generic methods
 * - Graceful error handling with clear exceptions
 * - Consistent response format
 * - Support for both typed and untyped processing
 */
@Component
public class ProcessorRequestSerializer extends BaseRequestSerializer<EntityProcessorCalculationRequest, EntityProcessorCalculationResponse> {

    private final JsonUtils jsonUtils;

    public ProcessorRequestSerializer(ObjectMapper objectMapper, JsonUtils jsonUtils) {
        super(objectMapper);
        this.jsonUtils = jsonUtils;
    }

    // ========================================
    // IMPLEMENTATION OF ABSTRACT METHODS
    // ========================================

    @Override
    public ObjectNode extractPayload(@NotNull EntityProcessorCalculationRequest request) {
        validateRequest(request);
        return (ObjectNode) request.getPayload().getData();
    }

    @Override
    protected void validateRequest(@NotNull EntityProcessorCalculationRequest request) {
        if (request.getPayload() == null) {
            throw new IllegalArgumentException("Request payload cannot be null");
        }
        if (request.getPayload().getData() == null) {
            throw new IllegalArgumentException("Request payload data cannot be null");
        }
    }

    @Override
    public EntityProcessorCalculationResponse createResponse(@NotNull EntityProcessorCalculationRequest request) {
        EntityProcessorCalculationResponse response = new EntityProcessorCalculationResponse();
        response.setId(request.getId());
        response.setPayload(request.getPayload()); // Copy payload structure
        response.setEntityId(request.getEntityId());
        response.setRequestId(request.getRequestId());
        return response;
    }

    @Override
    public EntityProcessorCalculationResponse createErrorResponse(
            @NotNull EntityProcessorCalculationRequest request,
            @NotNull String errorCode,
            @NotNull String errorMessage
    ) {
        EntityProcessorCalculationResponse response = createResponse(request);
        response.setSuccess(false);

        Error error = new Error();
        error.setCode(errorCode);
        error.setMessage(errorMessage);
        response.setError(error);

        return response;
    }

    // ========================================
    // PROCESSOR-SPECIFIC METHODS
    // ========================================

    /**
     * Creates a successful response from a processed CyodaEntity.
     * Automatically converts the entity back to the response format.
     *
     * @param request the original request
     * @param entity the processed CyodaEntity
     * @return EntityProcessorCalculationResponse with the entity data
     */
    public EntityProcessorCalculationResponse createSuccessResponse(@NotNull EntityProcessorCalculationRequest request, CyodaEntity entity) {
        try {
            EntityProcessorCalculationResponse response = createResponse(request);
            ObjectNode result = objectMapper.valueToTree(entity);
            response.setSuccess(true);
            response.getPayload().setData(jsonUtils.getJsonNode(result));
            return response;
        } catch (Exception e) {
            logger.error("Error creating response from entity", e);
            return createErrorResponse(request, "JSON_PROCESSING_ERROR", "Failed to convert entity to response: " + e.getMessage());
        }
    }
}
