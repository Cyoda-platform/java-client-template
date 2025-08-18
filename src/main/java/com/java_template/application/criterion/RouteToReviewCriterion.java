package com.java_template.application.criterion;

import com.java_template.application.entity.mail.version_1.Mail;
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
public class RouteToReviewCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final double approvalThreshold = 0.75; // default
    private final boolean gloomAutoSend = false;

    public RouteToReviewCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(Mail.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Mail> context) {
        Mail m = context.entity();
        if (m == null) return EvaluationOutcome.fail("Mail missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
        Double conf = m.getClassificationConfidence();
        Boolean isHappy = m.getIsHappy();
        if (conf == null) conf = 0.0;
        if (isHappy == null) return EvaluationOutcome.success(); // route to review by processor logic
        if (conf < approvalThreshold) return EvaluationOutcome.fail("Confidence below threshold", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        if (Boolean.FALSE.equals(isHappy) && !gloomAutoSend) return EvaluationOutcome.fail("Gloomy mail requires review", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        return EvaluationOutcome.success();
    }
}
