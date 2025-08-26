package com.java_template.application.processor;
import com.java_template.application.entity.hnitem.version_1.HNItem;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

@Component
public class ValidateRequiredFieldsProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateRequiredFieldsProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ValidateRequiredFieldsProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing HNItem for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(HNItem.class)
            .validate(this::hasRequiredFields, "HNItem missing required fields 'id' or 'type'")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Checks HNItem has required fields id and type. If they are missing on the entity but present inside originalJson,
     * this method will attempt to extract them and populate the entity so subsequent processors have those values.
     */
    private boolean hasRequiredFields(HNItem entity) {
        if (entity == null) return false;

        // If already present on the entity, accept.
        if (entity.getId() != null && entity.getId() > 0 && entity.getType() != null && !entity.getType().isBlank()) {
            return true;
        }

        // Try to inspect originalJson to extract id and type if available.
        String orig = entity.getOriginalJson();
        if (orig == null || orig.isBlank()) {
            return false;
        }

        try {
            JsonNode node = objectMapper.readTree(orig);
            boolean hasId = node.has("id") && !node.get("id").isNull() && node.get("id").canConvertToLong();
            boolean hasType = node.has("type") && !node.get("type").isNull() && node.get("type").isTextual() && !node.get("type").asText().isBlank();

            if (hasId) {
                long parsedId = node.get("id").asLong();
                if (parsedId > 0) {
                    entity.setId(parsedId);
                }
            }
            if (hasType) {
                entity.setType(node.get("type").asText());
            }

            return entity.getId() != null && entity.getId() > 0 && entity.getType() != null && !entity.getType().isBlank();
        } catch (Exception e) {
            logger.debug("Failed to parse originalJson while validating required fields: {}", e.getMessage());
            return false;
        }
    }

    private HNItem processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<HNItem> context) {
        HNItem entity = context.entity();

        // Validate required HN fields: id and type (after extraction attempt in hasRequiredFields)
        List<String> missingFields = new ArrayList<>();
        if (entity.getId() == null || entity.getId() <= 0) {
            missingFields.add("id");
        }
        if (entity.getType() == null || entity.getType().isBlank()) {
            missingFields.add("type");
        }

        if (!missingFields.isEmpty()) {
            // Mark entity as failed due to missing required fields.
            entity.setStatus("FAILED");
            logger.warn("HNItem validation failed for technicalId={} missingFields={}", context.request().getEntityId(), missingFields);
        } else {
            // Validation passed
            entity.setStatus("VALIDATED");
            logger.info("HNItem validation passed for technicalId={} id={} type={}", context.request().getEntityId(), entity.getId(), entity.getType());
        }

        return entity;
    }
}