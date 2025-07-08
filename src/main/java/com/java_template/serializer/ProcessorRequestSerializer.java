package com.java_template.serializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.util.JsonUtils;
import com.java_template.common.workflow.CyodaEntity;
import org.cyoda.cloud.api.event.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.EntityProcessorCalculationResponse;
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
    public ObjectNode extractPayload(EntityProcessorCalculationRequest request) {
        validateRequest(request);
        return (ObjectNode) request.getPayload().getData();
    }

    @Override
    protected void validateRequest(EntityProcessorCalculationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("EntityProcessorCalculationRequest cannot be null");
        }
        if (request.getPayload() == null) {
            throw new IllegalArgumentException("Request payload cannot be null");
        }
        if (request.getPayload().getData() == null) {
            throw new IllegalArgumentException("Request payload data cannot be null");
        }
    }

    @Override
    public EntityProcessorCalculationResponse createResponse(EntityProcessorCalculationRequest request) {
        EntityProcessorCalculationResponse response = new EntityProcessorCalculationResponse();
        response.setId(request.getId());
        response.setPayload(request.getPayload()); // Copy payload structure
        return response;
    }

    @Override
    public EntityProcessorCalculationResponse createErrorResponse(EntityProcessorCalculationRequest request, String errorMessage) {
        EntityProcessorCalculationResponse response = createResponse(request);
        response.setSuccess(false);

        if (errorMessage != null) {
            // Optionally add error message to payload
            ObjectNode errorPayload = objectMapper.createObjectNode();
            errorPayload.put("error", errorMessage);
            try {
                response.getPayload().setData(jsonUtils.getJsonNode(errorPayload));
            } catch (Exception e) {
                logger.warn("Could not set error message in response", e);
            }
        }

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
    public EntityProcessorCalculationResponse createSuccessResponse(EntityProcessorCalculationRequest request, CyodaEntity entity) {
        try {
            EntityProcessorCalculationResponse response = createResponse(request);
            ObjectNode result = objectMapper.valueToTree(entity);
            response.setSuccess(true);
            response.getPayload().setData(jsonUtils.getJsonNode(result));
            return response;
        } catch (Exception e) {
            logger.error("Error creating response from entity", e);
            return createErrorResponse(request, "Failed to convert entity to response: " + e.getMessage());
        }
    }


    /**
     * Sets the result data in the response from ObjectNode.
     * @param response the response to update
     * @param result the ObjectNode result to set
     * @param success whether the processing was successful
     */
    public void setResponseData(EntityProcessorCalculationResponse response, ObjectNode result, boolean success) {
        try {
            response.setSuccess(success);
            response.getPayload().setData(jsonUtils.getJsonNode(result));
        } catch (Exception e) {
            logger.error("Error setting response data", e);
            response.setSuccess(false);
        }
    }



    /**
     * Convenience method to process ObjectNode and create response.
     * Handles success flag extraction from result.
     * 
     * @param request the original request
     * @param result the processing result
     * @return EntityProcessorCalculationResponse
     */
    public EntityProcessorCalculationResponse createSuccessResponse(EntityProcessorCalculationRequest request, ObjectNode result) {
        EntityProcessorCalculationResponse response = createResponse(request);
        
        // Check if result contains success flag
        boolean success = result.path("success").asBoolean(true);
        result.remove("success"); // Remove success flag from payload
        
        setResponseData(response, result, success);
        return response;
    }



}
