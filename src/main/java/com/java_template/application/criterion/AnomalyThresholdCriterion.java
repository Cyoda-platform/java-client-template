package com.java_template.application.criterion;

import com.java_template.application.entity.activity.version_1.Activity;
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
public class AnomalyThresholdCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public AnomalyThresholdCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(Activity.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Activity> context) {
        Activity activity = context.entity();
        if (activity == null) {
            return EvaluationOutcome.fail("Activity missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        // If anomalyFlag already set true by processor consider threshold exceeded
        if (Boolean.TRUE.equals(activity.getAnomalyFlag())) {
            return EvaluationOutcome.success();
        }
        // Simple rule: purchases with metadata.highValue = true are anomalies
        if ("purchase".equalsIgnoreCase(activity.getType()) && activity.getMetadata() != null && activity.getMetadata().has("highValue") && activity.getMetadata().get("highValue").asBoolean(false)) {
            return EvaluationOutcome.success();
        }
        return EvaluationOutcome.fail("no anomaly detected", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }
}
