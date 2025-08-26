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
public class ReportCompleteCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ReportCompleteCriterion(SerializerFactory serializerFactory) {
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
         MonthlyReport entity = context.entity();

         // Validate required identifying fields
         if (entity.getMonth() == null || entity.getMonth().isBlank()) {
             return EvaluationOutcome.fail("month is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getGeneratedAt() == null || entity.getGeneratedAt().isBlank()) {
             return EvaluationOutcome.fail("generatedAt is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Business rule: report should be in GENERATING state before rendering
         if (entity.getStatus() == null || !entity.getStatus().equals("GENERATING")) {
             return EvaluationOutcome.fail("report must be in GENERATING status to proceed to rendering", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Data quality checks for metrics
         Integer total = entity.getTotalUsers();
         Integer nw = entity.getNewUsers();
         Integer invalid = entity.getInvalidUsers();

         if (total == null || total < 0) {
             return EvaluationOutcome.fail("totalUsers is missing or negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (nw == null || nw < 0) {
             return EvaluationOutcome.fail("newUsers is missing or negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (invalid == null || invalid < 0) {
             return EvaluationOutcome.fail("invalidUsers is missing or negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Consistency: totalUsers == newUsers + invalidUsers
         if (total.intValue() != (nw.intValue() + invalid.intValue())) {
             return EvaluationOutcome.fail("totalUsers does not equal newUsers + invalidUsers", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

        return EvaluationOutcome.success();
    }
}