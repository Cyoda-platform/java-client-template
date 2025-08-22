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

import java.util.List;

@Component
public class OnFirstAdoptionCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public OnFirstAdoptionCriterion(SerializerFactory serializerFactory) {
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
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<User> context) {
         User user = context.entity();
         if (user == null) {
             logger.debug("OnFirstAdoptionCriterion: received null user entity");
             return EvaluationOutcome.fail("User entity is null", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Ensure entity passes basic structural validation before applying business rule
         try {
             if (!user.isValid()) {
                 logger.debug("OnFirstAdoptionCriterion: user.isValid() == false for user id={}", user.getId());
                 return EvaluationOutcome.fail("User data invalid", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         } catch (Exception e) {
             logger.debug("OnFirstAdoptionCriterion: exception during isValid check", e);
             return EvaluationOutcome.fail("User data validation error", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         List<String> history = user.getAdoptionHistory();
         if (history == null || history.isEmpty()) {
             // No adoptions recorded — criterion not satisfied
             return EvaluationOutcome.fail("No adoption history recorded for user", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         if (history.size() == 1) {
             // Exactly one adoption => this is the first adoption
             return EvaluationOutcome.success();
         }

         // More than one adoption means this is not the first adoption
         return EvaluationOutcome.fail("User has more than one adoption; not a first adoption event", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }
}