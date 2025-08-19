package com.java_template.application.criterion;

import com.java_template.application.entity.inventoryreport.version_1.InventoryReport;
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

import java.time.OffsetDateTime;

@Component
public class RetentionExpiryCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public RetentionExpiryCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(InventoryReport.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<InventoryReport> context) {
        InventoryReport report = context.entity();
        if (report == null) return EvaluationOutcome.fail("Report is null", StandardEvalReasonCategories.VALIDATION_FAILURE);

        OffsetDateTime until = report.getRetentionUntil();
        if (until == null) return EvaluationOutcome.fail("No retentionUntil set", StandardEvalReasonCategories.VALIDATION_FAILURE);

        if (OffsetDateTime.now().isAfter(until)) {
            return EvaluationOutcome.success();
        }
        return EvaluationOutcome.fail("Retention not yet expired", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }
}
