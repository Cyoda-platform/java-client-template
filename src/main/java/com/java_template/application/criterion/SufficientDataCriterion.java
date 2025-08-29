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
public class SufficientDataCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public SufficientDataCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Report.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // must match exact criterion name
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Report> context) {
         Report report = context.entity();
         if (report == null) {
             logger.debug("SufficientDataCriterion: report entity is null");
             return EvaluationOutcome.fail("Report entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         Integer bookingsCount = report.getBookingsCount();
         if (bookingsCount == null || bookingsCount <= 0) {
             logger.debug("SufficientDataCriterion: insufficient bookingsCount={}", bookingsCount);
             return EvaluationOutcome.fail("Insufficient data: bookingsCount must be greater than 0", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         Double totalRevenue = report.getTotalRevenue();
         if (totalRevenue == null) {
             logger.debug("SufficientDataCriterion: totalRevenue is null");
             return EvaluationOutcome.fail("Insufficient data: totalRevenue is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (totalRevenue < 0) {
             logger.debug("SufficientDataCriterion: totalRevenue negative={}", totalRevenue);
             return EvaluationOutcome.fail("Invalid data: totalRevenue must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         Double avgPrice = report.getAverageBookingPrice();
         if (avgPrice == null) {
             logger.debug("SufficientDataCriterion: averageBookingPrice is null");
             return EvaluationOutcome.fail("Insufficient data: averageBookingPrice is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (avgPrice < 0) {
             logger.debug("SufficientDataCriterion: averageBookingPrice negative={}", avgPrice);
             return EvaluationOutcome.fail("Invalid data: averageBookingPrice must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         return EvaluationOutcome.success();
    }
}