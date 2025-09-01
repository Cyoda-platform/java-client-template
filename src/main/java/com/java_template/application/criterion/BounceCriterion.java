package com.java_template.application.criterion;

import com.java_template.application.entity.subscriber.version_1.Subscriber;
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
public class BounceCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public BounceCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Subscriber.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Subscriber> context) {
         Subscriber entity = context.entity();
         if (entity == null) {
             logger.warn("BounceCriterion invoked with null entity");
             return EvaluationOutcome.fail("Subscriber entity is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Email must be present for bounce evaluation
         String email = entity.getEmail();
         if (email == null || email.isBlank()) {
             return EvaluationOutcome.fail("Subscriber email is required for bounce detection", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // If subscriber already marked as BOUNCED, indicate business rule failure (repeated hard bounces detected/handled)
         String status = entity.getStatus();
         if (status != null && status.equalsIgnoreCase("BOUNCED")) {
             return EvaluationOutcome.fail("Subscriber has been marked as BOUNCED", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // No bounce indication found — pass criterion
         return EvaluationOutcome.success();
    }
}