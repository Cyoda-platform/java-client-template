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
public class DeliverySuccessCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public DeliverySuccessCriterion(SerializerFactory serializerFactory) {
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
        return modelSpec != null && className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Report> context) {
         Report entity = context.entity();
         if (entity == null) {
             logger.warn("DeliverySuccessCriterion: Report entity is null");
             return EvaluationOutcome.fail("Report entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Basic structural validation
         if (!entity.isValid()) {
             logger.warn("DeliverySuccessCriterion: Report failed basic validation (required fields missing or invalid). reportId={}, status={}", entity.getReportId(), entity.getStatus());
             return EvaluationOutcome.fail("Report failed basic validation", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Business rule: report must be marked as SENT
         String status = entity.getStatus();
         if (status == null || !status.equalsIgnoreCase("SENT")) {
             logger.info("DeliverySuccessCriterion: Report status is not SENT. reportId={}, status={}", entity.getReportId(), status);
             return EvaluationOutcome.fail("Report status is not SENT", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Data quality: sentAt timestamp must be present when status == SENT
         String sentAt = entity.getSentAt();
         if (sentAt == null || sentAt.isBlank()) {
             logger.info("DeliverySuccessCriterion: Report marked as SENT but sentAt is missing. reportId={}", entity.getReportId());
             return EvaluationOutcome.fail("Report is marked SENT but sentAt timestamp is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Optional: ensure reportId exists for traceability
         String reportId = entity.getReportId();
         if (reportId == null || reportId.isBlank()) {
             logger.info("DeliverySuccessCriterion: Report has no reportId despite being SENT. sentAt={}", sentAt);
             return EvaluationOutcome.fail("Report missing reportId", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Passed all delivery-success checks
         return EvaluationOutcome.success();
    }
}