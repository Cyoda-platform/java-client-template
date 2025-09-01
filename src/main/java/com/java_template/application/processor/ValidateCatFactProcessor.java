package com.java_template.application.processor;

import com.java_template.application.entity.catfact.version_1.CatFact;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class ValidateCatFactProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateCatFactProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public ValidateCatFactProcessor(SerializerFactory serializerFactory,
                                    EntityService entityService,
                                    ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CatFact for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(CatFact.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Lightweight validation used by this processor only.
     * We must not rely on the entity.isValid() here because some numeric fields
     * (sendCount, engagementScore) might be null at the moment of validation
     * and processor will set safe defaults later.
     *
     * Business validation for entering this processor:
     * - entity is not null
     * - text is present (non-null, non-blank)
     */
    private boolean isValidEntity(CatFact entity) {
        if (entity == null) return false;
        String text = entity.getText();
        return text != null && !text.isBlank();
    }

    private CatFact processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<CatFact> context) {
        CatFact entity = context.entity();

        // Ensure numeric defaults are present to satisfy entity invariants and avoid NPEs
        if (entity.getSendCount() == null) {
            entity.setSendCount(0);
        }
        if (entity.getEngagementScore() == null) {
            entity.setEngagementScore(0.0);
        }

        String text = entity.getText() != null ? entity.getText().trim() : "";
        // Business rule 1: non-empty
        if (text.isEmpty()) {
            entity.setValidationStatus("INVALID");
            logger.info("CatFact marked INVALID because text is empty. technicalId={}", entity.getTechnicalId());
            return entity;
        }

        // Business rule 2: length constraint
        final int MAX_LENGTH = 1000;
        if (text.length() > MAX_LENGTH) {
            entity.setValidationStatus("INVALID");
            logger.info("CatFact marked INVALID because text length {} exceeds {}", text.length(), MAX_LENGTH);
            return entity;
        }

        // Business rule 3: profanity filter (simple list)
        String lower = text.toLowerCase();
        String[] banned = new String[] {
            "damn",
            "hell",
            "crap",
            "shit",
            "fuck"
        };
        for (String b : banned) {
            String pattern = "\\b" + b + "\\b";
            if (lower.matches(".*" + pattern + ".*")) {
                entity.setValidationStatus("INVALID");
                logger.info("CatFact marked INVALID due to profanity match '{}'. technicalId={}", b, entity.getTechnicalId());
                return entity;
            }
        }

        // If all checks pass, mark as VALID
        entity.setValidationStatus("VALID");
        logger.info("CatFact validated as VALID. technicalId={}", entity.getTechnicalId());

        return entity;
    }
}