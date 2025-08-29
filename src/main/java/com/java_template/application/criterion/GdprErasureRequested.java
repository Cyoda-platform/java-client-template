package com.java_template.application.criterion;

import com.java_template.application.entity.user.version_1.User;
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
public class GdprErasureRequested implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public GdprErasureRequested(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(User.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // MUST use exact criterion name match
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<User> context) {
         User entity = context.entity();
         // Basic required identifier validation
         if (entity == null) {
             return EvaluationOutcome.fail("Entity payload is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getUserId() == null || entity.getUserId().isBlank()) {
             return EvaluationOutcome.fail("userId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Business rule: GDPR erasure request must set gdprState to 'erased_pending'
         String gdprState = entity.getGdprState();
         if (gdprState == null || gdprState.isBlank()) {
             return EvaluationOutcome.fail("gdprState must be set to 'erased_pending' when requesting erasure", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }
         if (!"erased_pending".equalsIgnoreCase(gdprState.trim())) {
             return EvaluationOutcome.fail("gdprState must be 'erased_pending' for gdpr erasure request", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Data quality: ownerOfPosts references (if present) must not contain blank entries
         if (entity.getOwnerOfPosts() != null) {
             for (String ref : entity.getOwnerOfPosts()) {
                 if (ref == null || ref.isBlank()) {
                     return EvaluationOutcome.fail("ownerOfPosts contains blank reference", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
             }
         }

         // Passed all checks
         return EvaluationOutcome.success();
    }
}