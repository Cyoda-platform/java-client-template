package com.java_template.application.criterion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.hackernewsitem.version_1.HackerNewsItem;
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

import java.io.IOException;

@Component
public class HackerNewsItemValidationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final ObjectMapper mapper = new ObjectMapper();

    public HackerNewsItemValidationCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(HackerNewsItem.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<HackerNewsItem> context) {
        HackerNewsItem entity = context.entity();
        if (entity == null) {
            return EvaluationOutcome.fail("Entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        String originalJson = entity.getOriginalJson();
        if (originalJson == null || originalJson.trim().isEmpty()) {
            return EvaluationOutcome.fail("originalJson is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        try {
            JsonNode node = mapper.readTree(originalJson);
            boolean hasId = node.has("id");
            boolean hasType = node.has("type");
            if (hasId && hasType) {
                return EvaluationOutcome.success();
            } else {
                return EvaluationOutcome.fail("Missing required fields: id and/or type", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
        } catch (IOException e) {
            logger.warn("Failed to parse originalJson during validation: {}", e.getMessage());
            return EvaluationOutcome.fail("originalJson is not valid JSON", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
    }
}
