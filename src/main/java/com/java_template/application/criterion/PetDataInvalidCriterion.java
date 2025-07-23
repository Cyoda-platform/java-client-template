package com.java_template.application.criterion;

import com.java_template.application.entity.PetCreationJob;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.config.Config;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PetDataInvalidCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public PetDataInvalidCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("PetDataInvalidCriterion initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();

        return serializer.withRequest(request)
            .evaluateEntity(PetCreationJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetDataInvalidCriterion".equals(modelSpec.operationName()) &&
               "petCreationJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(PetCreationJob entity) {
        // Business Logic:
        // Fail if petData is missing required fields or is invalid JSON
        if (entity == null) {
            return EvaluationOutcome.fail("Entity is null", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
        String petData = entity.getPetData();
        if (petData == null || petData.isBlank()) {
            return EvaluationOutcome.fail("Pet data must not be null or blank", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
        try {
            com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(petData);
            if (!root.hasNonNull("name") || root.get("name").asText().isBlank() ||
                !root.hasNonNull("category") || root.get("category").asText().isBlank() ||
                !root.hasNonNull("status") || root.get("status").asText().isBlank()) {
                return EvaluationOutcome.fail("Pet data missing one or more required fields", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
        } catch (Exception e) {
            // Invalid JSON
            return EvaluationOutcome.fail("Invalid petData JSON: " + e.getMessage(), StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
        // If we reach here, the petData is valid, so this criterion should fail (invalid) only if petData is invalid
        return EvaluationOutcome.fail("Pet data is valid, this criterion expects invalid data", StandardEvalReasonCategories.VALIDATION_FAILURE);
    }
}
