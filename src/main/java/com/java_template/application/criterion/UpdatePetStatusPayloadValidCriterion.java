package com.java_template.application.criterion;

import com.java_template.application.entity.PetJob;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class UpdatePetStatusPayloadValidCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public UpdatePetStatusPayloadValidCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("UpdatePetStatusPayloadValidCriterion initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();

        return serializer.withRequest(request)
            .evaluateEntity(PetJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "UpdatePetStatusPayloadValidCriterion".equals(modelSpec.operationName()) &&
               "petJob".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(PetJob entity) {
        // Validate payload contains required fields for UpdatePetStatus type
        if (!"UpdatePetStatus".equals(entity.getType())) {
            return EvaluationOutcome.fail("Job type must be 'UpdatePetStatus' for this criterion", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        String payload = entity.getPayload();
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (root.get("id") == null || root.get("id").asText().isBlank()) {
                return EvaluationOutcome.fail("Pet ID must be provided", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
            if (root.get("status") == null || root.get("status").asText().isBlank()) {
                return EvaluationOutcome.fail("Pet status must be provided", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
            String status = root.get("status").asText();
            if (!status.equals("AVAILABLE") && !status.equals("PENDING") && !status.equals("SOLD")) {
                return EvaluationOutcome.fail("Pet status must be one of AVAILABLE, PENDING, SOLD", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
        } catch (Exception e) {
            return EvaluationOutcome.fail("Payload is not valid JSON", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
        return EvaluationOutcome.success();
    }
}
