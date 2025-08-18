package com.java_template.application.criterion;

import com.java_template.application.entity.changeevent.version_1.ChangeEvent;
import com.java_template.application.entity.changeevent.version_1.DeliveryRecord;
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
public class AllDeliveriesFailedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public AllDeliveriesFailedCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(ChangeEvent.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<ChangeEvent> context) {
        ChangeEvent evt = context.entity();
        if (evt == null) {
            return EvaluationOutcome.fail("ChangeEvent null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        List<DeliveryRecord> recs = evt.getDeliveryRecords();
        if (recs == null || recs.isEmpty()) {
            return EvaluationOutcome.fail("No delivery records", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
        for (DeliveryRecord r : recs) {
            if (r.getResult() != DeliveryRecord.Result.FAILED) {
                return EvaluationOutcome.fail("Not all deliveries failed", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
        }
        return EvaluationOutcome.success();
    }
}
