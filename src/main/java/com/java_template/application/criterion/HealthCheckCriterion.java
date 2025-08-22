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
             logger.warn("HealthCheckCriterion: entity is null");
             return EvaluationOutcome.fail("Entity missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // 1) If pet is explicitly marked sick or unavailable, fail business rule
         String status = pet.getStatus();
         if (status != null) {
             String s = status.trim().toLowerCase();
             if ("sick".equals(s)) {
                 return EvaluationOutcome.fail("Pet status indicates sickness", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
             if ("unavailable".equals(s)) {
                 return EvaluationOutcome.fail("Pet is marked unavailable", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
         }

         // 2) Inspect health notes for obvious problem indicators
         String notes = pet.getHealthNotes();
         if (notes != null && !notes.isBlank()) {
             String lower = notes.toLowerCase();
             if (lower.contains("sick") || lower.contains("ill") || lower.contains("injury") || lower.contains("injured") || lower.contains("medical") || lower.contains("disease")) {
                 return EvaluationOutcome.fail("Health notes indicate potential issues", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
         }

         // 3) Ensure there is at least one photo (health checks rely on visual inspection/records)
         List<String> photos = pet.getPhotos();
         if (photos == null || photos.isEmpty()) {
             return EvaluationOutcome.fail("Pet has no photos", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         // photos list entries should be non-blank (Pet.isValid() already enforces this, but double-check defensively)
         for (String p : photos) {
             if (p == null || p.isBlank()) {
                 return EvaluationOutcome.fail("Pet has invalid photo references", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // If none of the checks failed, consider health check passed
         return EvaluationOutcome.success();
    }
}