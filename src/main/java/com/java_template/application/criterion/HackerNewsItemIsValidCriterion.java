package com.java_template.application.criterion;

import com.java_template.application.entity.HackerNewsItem;
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
public class HackerNewsItemIsValidCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public HackerNewsItemIsValidCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("HackerNewsItemIsValidCriterion initialized with SerializerFactory");
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
        return "HackerNewsItemIsValidCriterion".equals(modelSpec.operationName()) &&
               "hackerNewsItem".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(HackerNewsItem entity) {
        // Validate content is not null or blank
        if (entity.getContent() == null || entity.getContent().isBlank()) {
            return EvaluationOutcome.fail("Content must not be blank", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        // Validate content JSON contains both 'id' and 'type' fields
        // Since content is a JSON string, parse and check
        try {
            com.fasterxml.jackson.databind.JsonNode rootNode = new com.fasterxml.jackson.databind.ObjectMapper().readTree(entity.getContent());
            if (!rootNode.hasNonNull("id")) {
                return EvaluationOutcome.fail("Content missing required 'id' field", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
            if (!rootNode.hasNonNull("type")) {
                return EvaluationOutcome.fail("Content missing required 'type' field", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
        } catch (Exception e) {
            return EvaluationOutcome.fail("Content is not valid JSON", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}
