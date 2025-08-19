package com.java_template.application.criterion;

import com.java_template.application.entity.reportjob.version_1.ReportJob;
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

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Component
public class ValidateJobCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ValidateJobCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(ReportJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<ReportJob> context) {
        ReportJob job = context.entity();
        if (job == null) {
            return EvaluationOutcome.fail("ReportJob is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (job.getTechnicalId() == null || job.getTechnicalId().isEmpty()) {
            return EvaluationOutcome.fail("technicalId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (job.getInitiatedBy() == null || job.getInitiatedBy().isEmpty()) {
            return EvaluationOutcome.fail("initiatedBy is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        // Date range sanity
        try {
            if (job.getFilterDateFrom() != null && job.getFilterDateTo() != null) {
                LocalDate from = LocalDate.parse(job.getFilterDateFrom());
                LocalDate to = LocalDate.parse(job.getFilterDateTo());
                if (from.isAfter(to)) {
                    return EvaluationOutcome.fail("filterDateFrom must be on or before filterDateTo", StandardEvalReasonCategories.VALIDATION_FAILURE);
                }
            }
        } catch (DateTimeParseException e) {
            return EvaluationOutcome.fail("Invalid date format for filterDateFrom/to", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Price range
        if (job.getMinPrice() != null && job.getMaxPrice() != null) {
            if (job.getMinPrice() > job.getMaxPrice()) {
                return EvaluationOutcome.fail("minPrice must be <= maxPrice", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
        }

        // grouping check
        if (job.getGrouping() != null) {
            String g = job.getGrouping();
            if (!(g.equalsIgnoreCase("DAILY") || g.equalsIgnoreCase("WEEKLY") || g.equalsIgnoreCase("MONTHLY"))) {
                return EvaluationOutcome.fail("grouping must be DAILY, WEEKLY or MONTHLY", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
        }

        return EvaluationOutcome.success();
    }
}
