package com.java_template.application.criterion;

import com.java_template.application.entity.report.version_1.Report;
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
public class DeliveryFailureCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public DeliveryFailureCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Report.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Report> context) {
         Report report = context.entity();
         if (report == null) {
             logger.warn("DeliveryFailureCriterion: received null Report entity");
             return EvaluationOutcome.fail("Report entity is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String status = report.getStatus();
         if (status == null || status.isBlank()) {
             return EvaluationOutcome.fail("Report status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Detect permanent delivery failure: report explicitly marked FAILED and not sent
         if ("FAILED".equalsIgnoreCase(status)) {
             String sentAt = report.getSentAt();
             String reportId = report.getReportId() == null ? "<unknown>" : report.getReportId();

             if (sentAt == null || sentAt.isBlank()) {
                 logger.info("DeliveryFailureCriterion: detected delivery failure for reportId={}", reportId);
                 return EvaluationOutcome.fail(
                     "Delivery failed for reportId=" + reportId,
                     StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
                 );
             } else {
                 // Inconsistent state: marked FAILED but has sent timestamp — treat as data quality issue
                 logger.warn("DeliveryFailureCriterion: report marked FAILED but sentAt present for reportId={}", reportId);
                 return EvaluationOutcome.fail(
                     "Report marked FAILED but sentAt is present for reportId=" + reportId,
                     StandardEvalReasonCategories.DATA_QUALITY_FAILURE
                 );
             }
         }

         // Not a delivery failure
         return EvaluationOutcome.success();
    }
}