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
import java.util.Set;

@Component
public class PetValidationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public PetValidationCriterion(SerializerFactory serializerFactory) {
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
         Pet entity = context.entity();

         if (entity == null) {
             logger.debug("PetValidationCriterion: entity is null");
             return EvaluationOutcome.fail("Pet entity is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // id is required for persisted domain objects
         if (entity.getId() == null || entity.getId().isBlank()) {
             return EvaluationOutcome.fail("Pet id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required business fields
         if (entity.getName() == null || entity.getName().isBlank()) {
             return EvaluationOutcome.fail("Pet name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (entity.getSpecies() == null || entity.getSpecies().isBlank()) {
             return EvaluationOutcome.fail("Pet species is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (entity.getStatus() == null || entity.getStatus().isBlank()) {
             return EvaluationOutcome.fail("Pet status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate status values against allowed lifecycle states
         Set<String> allowedStatuses = Set.of("available", "pending", "adopted", "removed");
         String status = entity.getStatus().toLowerCase();
         if (!allowedStatuses.contains(status)) {
             return EvaluationOutcome.fail("Unknown pet status: " + entity.getStatus(), StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Age, if present, must be non-negative
         Integer age = entity.getAge();
         if (age != null && age < 0) {
             return EvaluationOutcome.fail("Pet age must be non-negative", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Gender, if present, should be one of expected values
         if (entity.getGender() != null && !entity.getGender().isBlank()) {
             String gender = entity.getGender().toLowerCase();
             Set<String> allowedGenders = Set.of("male", "female", "unknown");
             if (!allowedGenders.contains(gender)) {
                 return EvaluationOutcome.fail("Invalid pet gender value: " + entity.getGender(), StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // Source metadata quality checks
         Pet.SourceMetadata sm = entity.getSourceMetadata();
         if (sm != null) {
             if (sm.getExternalId() == null || sm.getExternalId().isBlank()) {
                 return EvaluationOutcome.fail("Source metadata.externalId is required when sourceMetadata is provided", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             if (sm.getSource() == null || sm.getSource().isBlank()) {
                 return EvaluationOutcome.fail("Source metadata.source is required when sourceMetadata is provided", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // Photos quality: if present, entries must not be blank
         List<String> photos = entity.getPhotos();
         if (photos != null) {
             for (String p : photos) {
                 if (p == null || p.isBlank()) {
                     return EvaluationOutcome.fail("Photo entries must not be blank", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
             }
         }

         // Basic breed normalization expectation: if breed present it should not be blank
         if (entity.getBreed() != null && entity.getBreed().isBlank()) {
             return EvaluationOutcome.fail("Breed, if provided, must not be blank", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // All checks passed
         return EvaluationOutcome.success();
    }
}