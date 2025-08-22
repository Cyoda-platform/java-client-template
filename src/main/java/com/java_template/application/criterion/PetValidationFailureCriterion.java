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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
public class PetValidationFailureCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public PetValidationFailureCriterion(SerializerFactory serializerFactory) {
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
             logger.debug("Pet entity is null in context");
             return EvaluationOutcome.fail("Pet entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required fields
         if (entity.getId() == null || entity.getId().isBlank()) {
             return EvaluationOutcome.fail("id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getName() == null || entity.getName().isBlank()) {
             return EvaluationOutcome.fail("name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getSpecies() == null || entity.getSpecies().isBlank()) {
             return EvaluationOutcome.fail("species is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getStatus() == null || entity.getStatus().isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Data quality checks
         if (entity.getAge() != null && entity.getAge() < 0) {
             return EvaluationOutcome.fail("age must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Validate photos if present: non-blank and valid URLs
         List<String> photos = entity.getPhotos();
         if (photos != null) {
             for (String p : photos) {
                 if (p == null || p.isBlank()) {
                     return EvaluationOutcome.fail("photos must not contain blank entries", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
                 try {
                     new URL(p);
                 } catch (MalformedURLException e) {
                     return EvaluationOutcome.fail("photo URL is invalid: " + p, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
             }
         }

         // Source metadata validation (use existing getters only)
         Pet.SourceMetadata sm = entity.getSourceMetadata();
         if (sm != null) {
             if (sm.getExternalId() == null || sm.getExternalId().isBlank()) {
                 return EvaluationOutcome.fail("source_metadata.externalId is required when source_metadata is provided", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             if (sm.getSource() == null || sm.getSource().isBlank()) {
                 return EvaluationOutcome.fail("source_metadata.source is required when source_metadata is provided", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // Business rules: enforce allowed statuses and genders (if provided)
         Set<String> allowedStatuses = Set.of("available", "pending", "adopted", "removed");
         if (!allowedStatuses.contains(entity.getStatus())) {
             return EvaluationOutcome.fail("status must be one of " + allowedStatuses, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         String gender = entity.getGender();
         if (gender != null && !gender.isBlank()) {
             Set<String> allowedGenders = Set.of("male", "female", "unknown");
             if (!allowedGenders.contains(gender)) {
                 return EvaluationOutcome.fail("gender must be one of " + allowedGenders, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // Optional sanity checks: name length and species length
         if (entity.getName() != null && entity.getName().length() > 250) {
             return EvaluationOutcome.fail("name is too long", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (entity.getSpecies() != null && entity.getSpecies().length() > 100) {
             return EvaluationOutcome.fail("species is too long", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // All checks passed
         return EvaluationOutcome.success();
    }
}