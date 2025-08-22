package com.java_template.application.criterion;

import com.java_template.application.entity.laureate.version_1.Laureate;
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

import java.util.ArrayList;
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
            .evaluateEntity(Laureate.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Laureate> context) {
         Laureate entity = context.entity();
         if (entity == null) {
             logger.warn("Laureate entity is null in NoMatchCriterion");
             return EvaluationOutcome.fail("Laureate entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Use entity's own validation where possible, but provide detailed messages for failures
         List<String> errors = new ArrayList<>();

         if (entity.getExternalId() == null || entity.getExternalId().isBlank()) {
             errors.add("externalId is required");
         }
         if (entity.getFullName() == null || entity.getFullName().isBlank()) {
             errors.add("fullName is required");
         }
         if (entity.getPrizeCategory() == null || entity.getPrizeCategory().isBlank()) {
             errors.add("prizeCategory is required");
         }
         if (entity.getPrizeYear() == null) {
             errors.add("prizeYear is required");
         }
         // If optional string fields are provided they must not be blank
         if (entity.getRawPayload() != null && entity.getRawPayload().isBlank()) {
             errors.add("rawPayload, if provided, must not be blank");
         }
         if (entity.getFirstSeenTimestamp() != null && entity.getFirstSeenTimestamp().isBlank()) {
             errors.add("firstSeenTimestamp, if provided, must not be blank");
         }
         if (entity.getLastSeenTimestamp() != null && entity.getLastSeenTimestamp().isBlank()) {
             errors.add("lastSeenTimestamp, if provided, must not be blank");
         }

         if (!errors.isEmpty()) {
             String message = String.join("; ", errors);
             logger.warn("NoMatchCriterion validation failed for Laureate id={} : {}", entity.getId(), message);
             return EvaluationOutcome.fail(message, StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // As a final sanity check, also respect entity.isValid() implementation
         if (!entity.isValid()) {
             logger.warn("Laureate.isValid() returned false for id={}", entity.getId());
             return EvaluationOutcome.fail("Laureate entity failed domain validation", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         return EvaluationOutcome.success();
    }
}