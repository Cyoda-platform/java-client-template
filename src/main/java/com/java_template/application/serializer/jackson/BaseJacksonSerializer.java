package com.java_template.application.serializer.jackson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.workflow.CyodaEntity;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

public abstract class BaseJacksonSerializer<TRequest> {

    protected final Logger logger = LoggerFactory.getLogger(this.getClass());
    protected final ObjectMapper objectMapper;

    protected BaseJacksonSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    protected <T extends CyodaEntity> T extractEntity(
            TRequest request,
            Class<T> clazz,
            Function<TRequest, JsonNode> payloadExtractor) {
        try {
            validateRequest(request);

            JsonNode payload = payloadExtractor.apply(request);
            T entity = objectMapper.treeToValue(payload, clazz);

            logger.debug("Successfully extracted entity of type {} from request", clazz.getSimpleName());
            return entity;

        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to convert payload to entity: " + e.getMessage(), e);
        }
    }

    protected JsonNode extractPayload(TRequest request, Function<TRequest, JsonNode> payloadExtractor) {
        validateRequest(request);
        return payloadExtractor.apply(request);
    }

    /**
     * Converts a CyodaEntity to JsonNode using Jackson ObjectMapper.
     * This method is public to allow processors and criteria to use it for entity conversion.
     *
     * @param entity the CyodaEntity to convert
     * @param <T> the type of the entity extending CyodaEntity
     * @return JsonNode representation of the entity
     * @throws RuntimeException if conversion fails
     */
    public <T extends CyodaEntity> JsonNode entityToJsonNode(T entity) {
        try {
            if (entity == null) {
                throw new IllegalArgumentException("Entity is null");
            }

            JsonNode jsonNode = objectMapper.valueToTree(entity);
            logger.debug("Successfully converted entity of type {} to JsonNode", entity.getClass().getSimpleName());
            return jsonNode;

        } catch (Exception e) {
            logger.error("Error converting entity of type {} to JsonNode",
                entity != null ? entity.getClass().getSimpleName() : "null", e);
            throw new RuntimeException("Failed to convert entity to JsonNode: " + e.getMessage(), e);
        }
    }

    protected abstract void validateRequest(@NotNull TRequest request);

}
