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

import java.util.List;

@Component
public class IsHappyCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public IsHappyCriterion(SerializerFactory serializerFactory) {
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
        Mail mail = context.entity();
        if (mail == null) {
            return EvaluationOutcome.fail("Mail entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        List<String> list = mail.getMailList();
        if (list == null || list.isEmpty()) {
            return EvaluationOutcome.fail("mailList must be present and non-empty", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        // If isHappy provided, honor it
        if (mail.getIsHappy() != null) {
            if (mail.getIsHappy()) {
                return EvaluationOutcome.success();
            } else {
                return EvaluationOutcome.fail("isHappy explicitly false", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
        }
        // Deterministic fallback: if any recipient contains "happy" then happy
        boolean happy = list.stream().filter(e -> e != null).anyMatch(e -> e.toLowerCase().contains("happy"));
        if (happy) {
            return EvaluationOutcome.success();
        }
        return EvaluationOutcome.fail("Determined not happy by fallback rule", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }
}
