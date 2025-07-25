package com.java_template.application.criterion;

import com.java_template.application.entity.HNItem;
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
public class HNItemTypeAndIdCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public HNItemTypeAndIdCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("HNItemTypeAndIdCriterion initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();

        return serializer.withRequest(request)
            .evaluateEntity(HNItem.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "HNItemTypeAndIdCriterion".equals(modelSpec.operationName()) &&
               "hnItem".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(HNItem entity) {
        // Validate that payload is non-null and not blank
        if (entity.getPayload() == null || entity.getPayload().isBlank()) {
            return EvaluationOutcome.fail("Payload must be present", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check JSON payload for keys "type" and "id"
        try {
            // Parse JSON string payload
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode jsonNode = mapper.readTree(entity.getPayload());

            if (!jsonNode.hasNonNull("type") || jsonNode.get("type").asText().isBlank()) {
                return EvaluationOutcome.fail("Payload missing required 'type' field", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
            if (!jsonNode.hasNonNull("id") || jsonNode.get("id").asText().isBlank()) {
                return EvaluationOutcome.fail("Payload missing required 'id' field", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
        } catch (Exception e) {
            return EvaluationOutcome.fail("Payload is not valid JSON", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}
