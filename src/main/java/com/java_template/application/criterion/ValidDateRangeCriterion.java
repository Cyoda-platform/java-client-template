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
public class ValidDateRangeCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ValidDateRangeCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(ReportJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<ReportJob> context) {
         ReportJob entity = context.entity();

         if (entity == null) {
             logger.warn("ReportJob entity is null in ValidDateRangeCriterion");
             return EvaluationOutcome.fail("Report job payload is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         ReportJob.Filters filters = entity.getFilters();
         if (filters == null) {
             logger.warn("Filters not provided on ReportJob: {}", entity.getName());
             return EvaluationOutcome.fail("Filters with date range are required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String dateFrom = filters.getDateFrom();
         String dateTo = filters.getDateTo();

         if (dateFrom == null || dateFrom.isBlank()) {
             logger.debug("dateFrom missing for ReportJob: {}", entity.getName());
             return EvaluationOutcome.fail("filters.dateFrom is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (dateTo == null || dateTo.isBlank()) {
             logger.debug("dateTo missing for ReportJob: {}", entity.getName());
             return EvaluationOutcome.fail("filters.dateTo is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         LocalDate fromDate;
         LocalDate toDate;
         try {
             fromDate = LocalDate.parse(dateFrom);
         } catch (DateTimeParseException ex) {
             logger.debug("Invalid dateFrom format: {} for ReportJob: {}", dateFrom, entity.getName());
             return EvaluationOutcome.fail("filters.dateFrom must be a valid ISO date (yyyy-MM-dd)", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         try {
             toDate = LocalDate.parse(dateTo);
         } catch (DateTimeParseException ex) {
             logger.debug("Invalid dateTo format: {} for ReportJob: {}", dateTo, entity.getName());
             return EvaluationOutcome.fail("filters.dateTo must be a valid ISO date (yyyy-MM-dd)", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (fromDate.isAfter(toDate)) {
             logger.debug("Invalid date range: dateFrom {} is after dateTo {} for ReportJob: {}", fromDate, toDate, entity.getName());
             return EvaluationOutcome.fail("filters.dateFrom must be on or before filters.dateTo", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // All checks passed
         return EvaluationOutcome.success();
    }
}