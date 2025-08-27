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
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
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
             return EvaluationOutcome.fail("Missing response code from upstream", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Success when upstream returned HTTP 200
         if (code == 200) {
             return EvaluationOutcome.success();
         }

         // 404 is a known negative outcome (not found) - treat as business rule failure for downstream handling
         if (code == 404) {
             return EvaluationOutcome.fail("Upstream indicated resource not found (404)", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Any other non-200 response is treated as business rule failure
         return EvaluationOutcome.fail("Upstream returned non-success response code: " + code, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }
}