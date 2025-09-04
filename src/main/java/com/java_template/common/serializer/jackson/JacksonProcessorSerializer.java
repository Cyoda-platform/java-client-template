package com.java_template.common.serializer.jackson;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.SerializerEnum;
import com.java_template.common.serializer.ResponseBuilder;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.workflow.CyodaEntity;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

/**
 * ABOUTME: Jackson-based implementation of ProcessorSerializer providing JSON serialization
 * and deserialization for workflow processor request and response handling.
 */
@Component
public class JacksonProcessorSerializer extends BaseJacksonSerializer<EntityProcessorCalculationRequest>
        implements ProcessorSerializer {

    public JacksonProcessorSerializer(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public <T extends CyodaEntity> EntityWithMetadata<T> extractEntityWithMetadata(EntityProcessorCalculationRequest request, Class<T> clazz) {
        return EntityWithMetadata.fromDataPayload(request.getPayload(), clazz, objectMapper);
    }

    @Override
    public JsonNode extractPayload(EntityProcessorCalculationRequest request) {
        return super.extractPayload(request, req -> req.getPayload().getData());
    }

    @Override
    public <T extends CyodaEntity> JsonNode entityToJsonNode(T entity) {
        return super.entityToJsonNode(entity);
    }

    @Override
    public ResponseBuilder.ProcessorResponseBuilder responseBuilder(EntityProcessorCalculationRequest request) {
        return ResponseBuilder.forProcessor(request);
    }

    @Override
    public String getType() {
        return SerializerEnum.JACKSON.getType();
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
}
