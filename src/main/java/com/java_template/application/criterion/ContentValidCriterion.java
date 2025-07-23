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
public class ContentValidCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public ContentValidCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("ContentValidCriterion initialized with SerializerFactory");
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
        return "ContentValidCriterion".equals(modelSpec.operationName()) &&
               "hnItem".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(HNItem entity) {
        // Validate presence of "id" and "type" fields in content JSON (business rule)

        if (entity.getContent() == null || entity.getContent().isBlank()) {
            return EvaluationOutcome.fail("Content is missing or empty", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Parse content JSON to check for "id" and "type" fields
        try {
            com.fasterxml.jackson.databind.JsonNode rootNode = 
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(entity.getContent());

            if (!rootNode.hasNonNull("id") || rootNode.get("id").asText().isBlank()) {
                return EvaluationOutcome.fail("Content JSON missing 'id' field or it is blank", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
            if (!rootNode.hasNonNull("type") || rootNode.get("type").asText().isBlank()) {
                return EvaluationOutcome.fail("Content JSON missing 'type' field or it is blank", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
        } catch (Exception e) {
            return EvaluationOutcome.fail("Content JSON parsing failed: " + e.getMessage(), StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}
