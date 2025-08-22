package com.java_template.application.criterion;

import com.java_template.application.entity.pet.version_1.Pet;
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
public class HealthCheckCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public HealthCheckCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Pet.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // Must match exact criterion name
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Pet> context) {
         Pet entity = context.entity();

         // Basic entity validity check using existing entity validation
         try {
             if (entity == null) {
                 return EvaluationOutcome.fail("Pet entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         } catch (Exception e) {
             logger.warn("Unexpected null pet in HealthCheckCriterion", e);
             return EvaluationOutcome.fail("Pet entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Use the entity's own validation for required fields and basic rules
         if (!entity.isValid()) {
             return EvaluationOutcome.fail("Pet failed basic validation (required fields/format)", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String status = entity.getStatus();
         String healthNotes = entity.getHealthNotes();
         boolean hasHealthNotes = healthNotes != null && !healthNotes.isBlank();

         // If health notes indicate sickness/injury/disease -> business rule failure
         if (hasHealthNotes && containsHealthIssue(healthNotes)) {
             return EvaluationOutcome.fail("Health check failed: health notes indicate illness or injury", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // If pet is being marked 'available' ensure it has media (photos) and no health issues
         if (status != null && status.equalsIgnoreCase("available")) {
             if (entity.getPhotos() == null || entity.getPhotos().isEmpty()) {
                 // We cannot access mediaStatus on this model, but photos being absent means media is not ready
                 return EvaluationOutcome.fail("Pet cannot be 'available' without media (photos missing)", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             // Already checked health notes above; repeat safe-check for defensive coding
             if (hasHealthNotes && containsHealthIssue(healthNotes)) {
                 return EvaluationOutcome.fail("Pet cannot be 'available' because health notes indicate issues", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
         }

         // Additional quality check: age must be non-negative (entity.isValid covers this) but defensive check:
         if (entity.getAge() != null && entity.getAge() < 0) {
             return EvaluationOutcome.fail("Age must be non-negative", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // All health checks passed
         return EvaluationOutcome.success();
    }

    private boolean containsHealthIssue(String notes) {
        if (notes == null) return false;
        String n = notes.toLowerCase();
        // common indicators of health problems
        return n.contains("sick")
            || n.contains("ill")
            || n.contains("injur")
            || n.contains("diseas")
            || n.contains("infection")
            || n.contains("fracture")
            || n.contains("needs vet")
            || n.contains("vet required")
            || n.contains("medical");
    }
}