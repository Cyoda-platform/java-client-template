package com.java_template.application.criterion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.hnitem.version_1.HNItem;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ValidateFieldsCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String className = this.getClass().getSimpleName();

    public ValidateFieldsCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
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
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<HNItem> context) {
        HNItem entity = context.entity();
        try {
            if (entity == null) {
                return EvaluationOutcome.fail("HNItem entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
            String original = entity.getOriginalJson();
            if (original == null || original.isBlank()) {
                return EvaluationOutcome.fail("originalJson is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
            JsonNode node = objectMapper.readTree(original);
            if (!node.has("id")) {
                return EvaluationOutcome.fail("originalJson.id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
            if (!node.has("type")) {
                return EvaluationOutcome.fail("originalJson.type is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
            return EvaluationOutcome.success();
        } catch (Exception e) {
            logger.error("Error validating HNItem fields: {}", e.getMessage(), e);
            return EvaluationOutcome.fail("Error parsing originalJson: " + e.getMessage(), StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
    }
}
