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
public class IsReadyForPublishCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public IsReadyForPublishCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Pet.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Pet> context) {
         Pet pet = context.entity();
         if (pet == null) {
             logger.debug("Pet entity is null in IsReadyForPublishCriterion");
             return EvaluationOutcome.fail("Pet entity is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Basic required fields for publishability
         if (pet.getName() == null || pet.getName().isBlank()) {
             return EvaluationOutcome.fail("Pet name is required for publishing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (pet.getSpecies() == null || pet.getSpecies().isBlank()) {
             return EvaluationOutcome.fail("Pet species is required for publishing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Media check: must have at least one valid photo to consider media present/processed (mediaStatus not modeled here)
         if (pet.getPhotos() == null || pet.getPhotos().isEmpty()) {
             return EvaluationOutcome.fail("No photos/media present - media must be provided before publishing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         // Ensure no blank photo entries
         for (String p : pet.getPhotos()) {
             if (p == null || p.isBlank()) {
                 return EvaluationOutcome.fail("Invalid photo entry detected", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // Health/status checks: pet must not be in exclusionary states
         String status = pet.getStatus();
         if (status != null) {
             String s = status.trim().toLowerCase();
             if ("sick".equals(s) || "unavailable".equals(s) || "adopted".equals(s)) {
                 return EvaluationOutcome.fail(
                     String.format("Pet status '%s' prevents publishing", status),
                     StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
                 );
             }
         }

         // Heuristic healthNotes check: if healthNotes explicitly mention sickness indicators, block publish.
         if (pet.getHealthNotes() != null && !pet.getHealthNotes().isBlank()) {
             String notes = pet.getHealthNotes().toLowerCase();
             if (notes.contains("sick") || notes.contains("ill") || notes.contains("unhealthy") || notes.contains("injur")) {
                 return EvaluationOutcome.fail("Health notes indicate the pet is not healthy", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
         }

         // Age sanity check (if present)
         if (pet.getAge() != null && pet.getAge() < 0) {
             return EvaluationOutcome.fail("Invalid age for pet", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // If all checks pass, the pet is considered ready for publish
         return EvaluationOutcome.success();
    }
}