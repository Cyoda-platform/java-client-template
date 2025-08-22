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

import java.util.Set;

@Component
public class IsValidPetCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public IsValidPetCriterion(SerializerFactory serializerFactory) {
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
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Pet> context) {
         Pet entity = context.entity();

         if (entity == null) {
             return EvaluationOutcome.fail("Pet entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required business id
         if (entity.getId() == null || entity.getId().isBlank()) {
             return EvaluationOutcome.fail("id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required pet name
         if (entity.getName() == null || entity.getName().isBlank()) {
             return EvaluationOutcome.fail("name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required species
         if (entity.getSpecies() == null || entity.getSpecies().isBlank()) {
             return EvaluationOutcome.fail("species is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required status
         if (entity.getStatus() == null || entity.getStatus().isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Photos must be non-null (can be empty list)
         if (entity.getPhotos() == null) {
             return EvaluationOutcome.fail("photos list must be present (can be empty)", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Age if present must be non-negative
         if (entity.getAge() != null && entity.getAge() < 0) {
             return EvaluationOutcome.fail("age must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Gender allowed values (if present)
         if (entity.getGender() != null && !entity.getGender().isBlank()) {
             String g = entity.getGender().trim().toLowerCase();
             Set<String> allowedGenders = Set.of("male", "female", "unknown");
             if (!allowedGenders.contains(g)) {
                 return EvaluationOutcome.fail("unsupported gender value: '" + entity.getGender() + "'", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
         }

         // Status should be one of expected lifecycle values (normalized check)
         if (entity.getStatus() != null && !entity.getStatus().isBlank()) {
             String s = entity.getStatus().trim().toLowerCase();
             // Accept common lifecycle statuses; allow others but treat unknown as business rule failure
             Set<String> allowedStatuses = Set.of("available", "reserved", "adopted", "created", "in_progress", "invalid", "pending", "completed", "failed", "notified");
             if (!allowedStatuses.contains(s)) {
                 return EvaluationOutcome.fail("unsupported status value: '" + entity.getStatus() + "'", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
         }

         // Source is optional; if present, ensure it's not blank
         if (entity.getSource() != null && entity.getSource().isBlank()) {
             return EvaluationOutcome.fail("source is present but blank", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Final holistic check using entity's own validation helper if provided
         try {
             if (!entity.isValid()) {
                 // isValid() already encapsulates required checks; provide a generic message
                 return EvaluationOutcome.fail("pet entity failed validity checks", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         } catch (Exception ex) {
             logger.warn("Error while calling isValid() for pet {}: {}", entity.getId(), ex.getMessage(), ex);
             // If isValid throws, consider it a data quality failure
             return EvaluationOutcome.fail("error validating pet entity: " + ex.getMessage(), StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

        return EvaluationOutcome.success();
    }
}