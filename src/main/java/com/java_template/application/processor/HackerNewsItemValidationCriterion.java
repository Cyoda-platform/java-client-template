package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.hackernewsitem.version_1.HackerNewsItem;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class HackerNewsItemValidationCriterion implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(HackerNewsItemValidationCriterion.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final ObjectMapper mapper = new ObjectMapper();

    public HackerNewsItemValidationCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing HackerNewsItem validation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(HackerNewsItem.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(HackerNewsItem entity) {
        return entity != null && entity.getOriginalJson() != null && !entity.getOriginalJson().isBlank();
    }

    private HackerNewsItem processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<HackerNewsItem> context) {
        HackerNewsItem entity = context.entity();
        try {
            String original = entity.getOriginalJson();
            JsonNode node = mapper.readTree(original);

            boolean hasId = false;
            boolean hasType = false;

            if (node.has("id") && !node.get("id").isNull() && node.get("id").canConvertToLong()) {
                long parsedId = node.get("id").longValue();
                entity.setId(parsedId);
                hasId = true;
            }

            if (node.has("type") && !node.get("type").isNull()) {
                String parsedType = node.get("type").asText();
                if (parsedType != null && !parsedType.isBlank()) {
                    entity.setType(parsedType);
                    hasType = true;
                }
            }

            // ensure importTimestamp is set during processing if not already
            if (entity.getImportTimestamp() == null) {
                entity.setImportTimestamp(Instant.now());
            }

            if (hasId && hasType) {
                entity.setState("VALID");
                entity.setValidationErrors(null);
            } else {
                entity.setState("INVALID");
                StringBuilder sb = new StringBuilder();
                if (!hasId) sb.append("missing id");
                if (!hasId && !hasType) sb.append(" and ");
                if (!hasType) sb.append("missing type");
                entity.setValidationErrors(sb.toString());
            }

        } catch (Exception e) {
            logger.warn("Failed to validate HackerNewsItem originalJson: {}", e.getMessage());
            // mark as invalid if parsing failed
            entity.setState("INVALID");
            entity.setValidationErrors("invalid originalJson: " + e.getMessage());
            if (entity.getImportTimestamp() == null) {
                entity.setImportTimestamp(Instant.now());
            }
        }
        return entity;
    }
}
