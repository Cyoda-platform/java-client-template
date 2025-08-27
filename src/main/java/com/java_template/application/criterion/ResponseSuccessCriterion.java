package com.java_template.application.criterion;

import com.java_template.application.entity.getuserjob.version_1.GetUserJob;
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
public class ResponseSuccessCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ResponseSuccessCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Business logic implemented in validateEntity method.
        return serializer.withRequest(request)
            .evaluateEntity(GetUserJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "ResponseSuccessCriterion".equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<GetUserJob> context) {
         GetUserJob entity = context.entity();

         // Ensure response code is present
         Integer code = entity.getResponseCode();
         if (code == null) {
             logger.debug("GetUserJob {}: missing responseCode", entity);
             return EvaluationOutcome.fail("Missing response code from upstream", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // If upstream returned HTTP 200 -> success, but verify job consistency
         if (code == 200) {
             // If job status indicates failure despite 200, treat as business rule failure
             String status = entity.getStatus();
             if (status != null && status.equalsIgnoreCase("FAILED")) {
                 return EvaluationOutcome.fail("Job marked as FAILED despite upstream 200 response", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }

             // If an error message is present on a successful response, treat as data quality issue
             String err = entity.getErrorMessage();
             if (err != null && !err.isBlank()) {
                 return EvaluationOutcome.fail("Error message present on successful upstream response", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }

             return EvaluationOutcome.success();
         }

         // 404 is a known negative outcome (not found) - treat as business rule failure for downstream handling
         if (code == 404) {
             return EvaluationOutcome.fail("Upstream indicated resource not found (404)", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Server errors (5xx) considered data quality / upstream stability issues
         if (code >= 500 && code <= 599) {
             return EvaluationOutcome.fail("Upstream server error returned: " + code, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Any other non-200 response is treated as business rule failure
         return EvaluationOutcome.fail("Upstream returned non-success response code: " + code, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }
}