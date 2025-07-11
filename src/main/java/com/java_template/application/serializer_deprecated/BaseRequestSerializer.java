package com.java_template.application.serializer_deprecated;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.workflow.CyodaEntity;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base abstract serializer providing common functionality for request/response handling.
 * <p>
 * This class eliminates code duplication between ProcessorRequestSerializer and CriteriaRequestSerializer
 * by providing shared extraction, validation, and utility methods.
 *
 * @param <REQ>  the request type (EntityProcessorCalculationRequest or EntityCriteriaCalculationRequest)
 * @param <RESP> the response type (EntityProcessorCalculationResponse or EntityCriteriaCalculationResponse)
 */
public abstract class BaseRequestSerializer<REQ, RESP> {

    protected final Logger logger = LoggerFactory.getLogger(this.getClass());
    protected final ObjectMapper objectMapper;

    protected BaseRequestSerializer(@NotNull ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // ========================================
    // COMMON EXTRACTION METHODS
    // ========================================

    /**
     * Extracts and converts the payload data from the request to the specified entity type.
     * This is the preferred method when you know the exact entity type.
     *
     * @param request the request object
     * @param clazz   the Class of the entity type to extract (Pet.class, PetFetchRequest.class, etc.)
     * @param <T>     the entity type that extends CyodaEntity
     * @return the entity of the specified type
     * @throws IllegalArgumentException if request or clazz is null
     * @throws RuntimeException         if conversion fails
     */
    public <T extends CyodaEntity> T extractEntity(@NotNull REQ request, @NotNull Class<T> clazz) throws JsonProcessingException {
        validateRequest(request);
        validateClass(clazz);

        ObjectNode payload = extractPayload(request);
        T entity = objectMapper.treeToValue(payload, clazz);
        logger.debug("Successfully extracted entity of type {} from request", clazz.getSimpleName());
        return entity;
    }

    /**
     * Extracts the payload data from the request as ObjectNode.
     * Use this for custom processing or when entity conversion is not needed.
     *
     * @param request the request object
     * @return ObjectNode containing the payload data
     * @throws IllegalArgumentException if request is null
     */
    public abstract ObjectNode extractPayload(@NotNull REQ request);

    // ========================================
    // ABSTRACT RESPONSE CREATION METHODS
    // ========================================

    /**
     * Creates a response from the request, copying common fields.
     * Implementation varies between processor and criteria responses.
     *
     * @param request the original request
     * @return response object with copied metadata
     */
    public abstract RESP createResponse(@NotNull REQ request);

    /**
     * Creates an error response from the request.
     * Implementation varies between processor and criteria responses.
     *
     * @param request      the original request
     * @param errorMessage optional error message
     * @return response object indicating failure
     */
    public abstract RESP createErrorResponse(
            @NotNull REQ request,
            @NotNull String errorCode,
            @NotNull String errorMessage);

    // ========================================
    // COMMON VALIDATION METHODS
    // ========================================

    /**
     * Validates that the request is not null and has required fields.
     * Subclasses can override this method to add specific validation logic.
     */
    protected abstract void validateRequest(@NotNull REQ request);

    /**
     * Validates that the class parameter is not null.
     */
    protected void validateClass(@NotNull Class<? extends CyodaEntity> clazz) {
    }
}
