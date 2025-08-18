package com.java_template.application.criterion;

import com.java_template.application.entity.weeklysend.version_1.WeeklySend;
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
public class SendFailureCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    // Failure threshold: if bounces exceed this percentage of recipients, treat as failure
    private static final double FAILURE_PERCENTAGE = 0.2; // 20%

    public SendFailureCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(WeeklySend.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<WeeklySend> context) {
        WeeklySend send = context.entity();
        if (send == null) {
            return EvaluationOutcome.fail("WeeklySend is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (send.getRecipients_count() == null || send.getRecipients_count() == 0) {
            return EvaluationOutcome.fail("No recipients", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        int bounces = send.getBounces_count() == null ? 0 : send.getBounces_count();
        double pct = (double) bounces / (double) send.getRecipients_count();
        if (pct >= FAILURE_PERCENTAGE) {
            return EvaluationOutcome.fail("Bounce rate too high", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}
