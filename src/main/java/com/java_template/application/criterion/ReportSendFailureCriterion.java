package com.java_template.application.criterion;

import com.java_template.application.entity.monthlyreport.version_1.MonthlyReport;
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
public class ReportSendFailureCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ReportSendFailureCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(MonthlyReport.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<MonthlyReport> context) {
         MonthlyReport report = context.entity();
         if (report == null) {
             return EvaluationOutcome.fail("MonthlyReport entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String status = report.getStatus();
         if (status == null || status.isBlank()) {
             return EvaluationOutcome.fail("Report status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Successful send -> PUBLISHED
         if ("PUBLISHED".equalsIgnoreCase(status)) {
             // When published, deliveryAt must be present
             String deliveryAt = report.getDeliveryAt();
             if (deliveryAt == null || deliveryAt.isBlank()) {
                 return EvaluationOutcome.fail("Published report missing deliveryAt", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             return EvaluationOutcome.success();
         }

         // Explicit failure recorded by send processor
         if ("FAILED".equalsIgnoreCase(status)) {
             String month = report.getMonth();
             String fileRef = report.getFileRef();
             StringBuilder msg = new StringBuilder("Report sending failed");
             if (month != null && !month.isBlank()) msg.append(" for month ").append(month);
             if (fileRef != null && !fileRef.isBlank()) msg.append(" (fileRef=").append(fileRef).append(")");
             return EvaluationOutcome.fail(msg.toString(), StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Not yet attempted to send or in intermediate states
         return EvaluationOutcome.fail("Report not published or failed yet (current status: " + status + ")", StandardEvalReasonCategories.VALIDATION_FAILURE);
    }
}