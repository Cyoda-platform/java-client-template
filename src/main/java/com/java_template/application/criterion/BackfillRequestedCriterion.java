package com.java_template.application.criterion;

import com.java_template.application.entity.subscriber.version_1.Subscriber;
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

import java.time.LocalDate;

@Component
public class BackfillRequestedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public BackfillRequestedCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(Subscriber.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Subscriber> context) {
        Subscriber s = context.entity();
        if (s == null) return EvaluationOutcome.fail("Subscriber payload missing", StandardEvalReasonCategories.VALIDATION_FAILURE);

        // Backfill only if subscriber is active and backfillFromDate is provided and parseable
        if (!Boolean.TRUE.equals(s.getActive())) {
            return EvaluationOutcome.fail("Subscriber not active", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        String from = s.getBackfillFromDate();
        if (from == null || from.isBlank()) {
            return EvaluationOutcome.fail("backfillFromDate not provided", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        try {
            LocalDate.parse(from);
        } catch (Exception e) {
            logger.warn("Invalid backfillFromDate format for subscriber {}: {}", s.getTechnicalId(), from);
            return EvaluationOutcome.fail("Invalid backfillFromDate format", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}
