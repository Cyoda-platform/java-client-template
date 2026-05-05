package com.java_template.application.criterion;

import com.java_template.application.entity.greeting.version_1.Greeting;
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

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * IsMorningCriterion - Checks if greeting timestamp is before 12:00
 */
@Component
public class IsMorningCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public IsMorningCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking IsMorning criteria for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(Greeting.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Greeting> context) {
        Greeting entity = context.entityWithMetadata().entity();

        if (entity == null || entity.getTimestamp() == null) {
            logger.warn("Greeting is null or has no timestamp");
            return EvaluationOutcome.fail("Greeting is null or timestamp missing", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        LocalTime time = entity.getTimestamp().toLocalTime();
        if (time.isBefore(LocalTime.of(12, 0))) {
            logger.debug("Greeting is in morning (before 12:00)");
            return EvaluationOutcome.success();
        }

        logger.debug("Greeting is NOT in morning (12:00 or later)");
        return EvaluationOutcome.fail("Time is 12:00 or later", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }
}

