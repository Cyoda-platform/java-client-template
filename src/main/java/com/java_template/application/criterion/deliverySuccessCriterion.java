package com.java_template.application.criterion;

import com.java_template.application.entity.weeklyreport.version_1.WeeklyReport;
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

import java.util.Map;

@Component
public class deliverySuccessCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public deliverySuccessCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
                .evaluateEntity(WeeklyReport.class, this::validateEntity)
                .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<WeeklyReport> context) {
        WeeklyReport r = context.entity();
        try {
            if (r.getDeliveryInfo() == null) return EvaluationOutcome.fail("no delivery info", StandardEvalReasonCategories.VALIDATION_FAILURE);
            Object status = r.getDeliveryInfo().get("emailStatus");
            if (status == null) return EvaluationOutcome.fail("no email status", StandardEvalReasonCategories.VALIDATION_FAILURE);
            String s = String.valueOf(status);
            if ("delivered".equalsIgnoreCase(s)) return EvaluationOutcome.success();
            return EvaluationOutcome.fail("email status " + s, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        } catch (Exception ex) {
            logger.error("deliverySuccessCriterion: unexpected error", ex);
            return EvaluationOutcome.fail("exception during delivery check", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
    }
}
