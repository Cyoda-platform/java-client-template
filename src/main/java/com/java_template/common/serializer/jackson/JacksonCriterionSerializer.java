package com.java_template.common.serializer.jackson;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.SerializerEnum;
import com.java_template.common.serializer.ResponseBuilder;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.workflow.CyodaEntity;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

/**
 * Jackson-based implementation of CriterionSerializer.
 * Provides sealed interface integration with Jackson serialization for criteria evaluation.
 */
@Component
public class JacksonCriterionSerializer extends BaseJacksonSerializer<EntityCriteriaCalculationRequest>
        implements CriterionSerializer {

    public JacksonCriterionSerializer(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public <T extends CyodaEntity> EntityWithMetadata<T> extractEntityWithMetadata(EntityCriteriaCalculationRequest request, Class<T> clazz) {
        return EntityWithMetadata.fromDataPayload(request.getPayload(), clazz, objectMapper);
    }

    @Override
    public JsonNode extractPayload(EntityCriteriaCalculationRequest request) {
        return super.extractPayload(request, req -> req.getPayload().getData());
    }

    @Override
    public <T extends CyodaEntity> JsonNode entityToJsonNode(T entity) {
        return super.entityToJsonNode(entity);
    }

    @Override
    public ResponseBuilder.CriterionResponseBuilder responseBuilder(EntityCriteriaCalculationRequest request) {
        return ResponseBuilder.forCriterion(request);
    }

    @Override
    public String getType() {
        return SerializerEnum.JACKSON.getType();
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
}
