package com.java_template.common.serializer.jackson;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.serializer.ProcessorSerializer.EntityReader;
import com.java_template.common.serializer.SerializerEnum;
import com.java_template.common.serializer.ResponseBuilder;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.workflow.CyodaEntity;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Jackson-based implementation of ProcessorSerializer.
 * Provides sealed interface integration with Jackson serialization.
 */
@Component
public class JacksonProcessorSerializer extends BaseJacksonSerializer<EntityProcessorCalculationRequest>
        implements ProcessorSerializer {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public JacksonProcessorSerializer(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public <T extends CyodaEntity> T extractEntity(EntityProcessorCalculationRequest request, Class<T> clazz) {
        return super.extractEntity(request, clazz, req -> req.getPayload().getData());
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

    @Override
    public ObjectNode toObjectNode(com.java_template.common.workflow.CyodaEntity entity) {
        try {
            JsonNode node = objectMapper.valueToTree(entity);
            if (node instanceof ObjectNode) return (ObjectNode) node;
            // If it's not an object node, wrap it
            ObjectNode wrapper = objectMapper.createObjectNode();
            wrapper.set("value", node);
            return wrapper;
        } catch (Exception e) {
            logger.error("Failed to convert entity to ObjectNode: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public <T extends CyodaEntity> EntityReader<T> toEntity(Class<T> clazz) {
        return node -> {
            try {
                return objectMapper.treeToValue(node, clazz);
            } catch (Exception e) {
                logger.error("Failed to read entity of type {} from node: {}", clazz.getSimpleName(), e.getMessage(), e);
                throw new RuntimeException(e);
            }
        };
    }
}
