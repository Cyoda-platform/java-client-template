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

import java.util.List;

@Component
public class NoMatchCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public NoMatchCriterion(SerializerFactory serializerFactory) {
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
         Subscriber subscriber = context.entity();

         if (subscriber == null) {
             logger.warn("No subscriber entity present in evaluation context");
             return EvaluationOutcome.fail("Subscriber entity missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // If subscriber is not active, they will never receive notifications -> business rule
         if (subscriber.getActive() == null || !subscriber.getActive()) {
             return EvaluationOutcome.fail("Subscriber is not active and will not receive matches", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Subscribed categories must exist and contain valid (non-blank) entries.
         List<String> cats = subscriber.getSubscribedCategories();
         if (cats == null || cats.isEmpty()) {
             return EvaluationOutcome.fail("Subscriber has no subscribed categories", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         for (String cat : cats) {
             if (cat == null || cat.isBlank()) {
                 return EvaluationOutcome.fail("Subscriber has blank category entry", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // Validate year range: must be present and parsable with from <= to.
         if (subscriber.getSubscribedYearRange() == null) {
             return EvaluationOutcome.fail("Subscriber has no subscribed year range", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         String from = subscriber.getSubscribedYearRange().getFrom();
         String to = subscriber.getSubscribedYearRange().getTo();
         if (from == null || from.isBlank() || to == null || to.isBlank()) {
             return EvaluationOutcome.fail("Subscribed year range contains empty bounds", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         try {
             int fromY = Integer.parseInt(from);
             int toY = Integer.parseInt(to);
             if (fromY > toY) {
                 return EvaluationOutcome.fail("Subscribed year range 'from' is later than 'to'", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         } catch (NumberFormatException nfe) {
             return EvaluationOutcome.fail("Subscribed year range bounds are not valid integers", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // If all checks passed, subscriber is properly configured to match laureates.
         return EvaluationOutcome.success();
    }
}