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
public class ResponseErrorCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ResponseErrorCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(GetUserJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<GetUserJob> context) {
         GetUserJob entity = context.entity();

         Integer responseCode = entity.getResponseCode();
         String requestUserId = entity.getRequestUserId();
         String errorMessage = entity.getErrorMessage();
         String status = entity.getStatus();

         logger.debug("Evaluating ResponseErrorCriterion for requestUserId={}, status={}, responseCode={}", requestUserId, status, responseCode);

         // Missing response code is a validation problem
         if (responseCode == null) {
             return EvaluationOutcome.fail("Missing response code from upstream", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // 200 is successful upstream response -> not an error for this criterion
         if (responseCode == 200) {
             return EvaluationOutcome.success();
         }

         // 404 means "not found" and is handled as a completed (non-error) case by the workflow
         if (responseCode == 404) {
             return EvaluationOutcome.success();
         }

         // Any other response code represents an upstream error condition -> business rule failure
         StringBuilder msg = new StringBuilder("Upstream returned code " + responseCode);
         if (errorMessage != null && !errorMessage.isBlank()) {
             msg.append(": ").append(errorMessage);
         }
         return EvaluationOutcome.fail(msg.toString(), StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }
}