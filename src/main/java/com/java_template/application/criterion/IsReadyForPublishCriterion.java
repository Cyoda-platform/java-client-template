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

import java.util.List;

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
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Pet.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // Must use exact criterion name
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Pet> context) {
         Pet entity = context.entity();
         if (entity == null) {
             logger.debug("IsReadyForPublishCriterion: entity is null");
             return EvaluationOutcome.fail("Entity missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Basic sanity: required fields for publishability
         if (entity.getId() == null || entity.getId().isBlank()) {
             return EvaluationOutcome.fail("Pet technical id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getName() == null || entity.getName().isBlank()) {
             return EvaluationOutcome.fail("Pet name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getSpecies() == null || entity.getSpecies().isBlank()) {
             return EvaluationOutcome.fail("Pet species is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Data quality: media must be present (project entity does not include mediaStatus field).
         List<String> photos = entity.getPhotos();
         if (photos == null || photos.isEmpty()) {
             return EvaluationOutcome.fail("No photos attached - media required before publish", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         // Ensure there are no blank photo entries
         for (String p : photos) {
             if (p == null || p.isBlank()) {
                 return EvaluationOutcome.fail("Photos contain invalid entries", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // Business rules: pet should not be in a disqualifying state
         String status = entity.getStatus();
         if (status != null) {
             String normalized = status.trim().toLowerCase();
             if ("sick".equals(normalized) || "unavailable".equals(normalized) || "adopted".equals(normalized) || "held".equals(normalized) || "reserved".equals(normalized)) {
                 return EvaluationOutcome.fail("Pet status prevents publishing: " + status, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
         }

         // Health check heuristic: if healthNotes explicitly indicate sickness, block publish.
         String healthNotes = entity.getHealthNotes();
         if (healthNotes != null && !healthNotes.isBlank()) {
             String notesLower = healthNotes.toLowerCase();
             if (notesLower.contains("sick") || notesLower.contains("ill") || notesLower.contains("injur")) {
                 return EvaluationOutcome.fail("Health notes indicate the pet is not healthy for publish", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
         }

         // All checks passed
         return EvaluationOutcome.success();
    }
}