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
             logger.warn("Pet entity is null in evaluation context");
             return EvaluationOutcome.fail("Pet entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required identifiers and core fields
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
             // Status is required per domain model; validation processor may default it,
             // but if missing here we treat it as validation failure.
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Age should be non-negative if present
         if (entity.getAge() != null && entity.getAge() < 0) {
             return EvaluationOutcome.fail("age must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Validate gender if present (domain expects specific values)
         String gender = entity.getGender();
         if (gender != null && !gender.isBlank()) {
             String normal = gender.trim().toLowerCase();
             if (!(normal.equals("male") || normal.equals("female") || normal.equals("unknown"))) {
                 return EvaluationOutcome.fail("gender must be one of [male, female, unknown]", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // Validate status against known domain states
         String status = entity.getStatus().trim().toLowerCase();
         if (!(status.equals("available") || status.equals("pending") || status.equals("adopted") || status.equals("removed") || status.equals("completed"))) {
             return EvaluationOutcome.fail("status has invalid value", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Photos: if present, ensure no null/blank entries
         List<String> photos = entity.getPhotos();
         if (photos != null) {
             for (String p : photos) {
                 if (p == null || p.isBlank()) {
                     return EvaluationOutcome.fail("photos must not contain empty entries", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
             }
         }

         // Source metadata quality checks
         Pet.SourceMetadata sm = entity.getSourceMetadata();
         if (sm != null) {
             if (sm.getExternalId() == null || sm.getExternalId().isBlank()) {
                 return EvaluationOutcome.fail("sourceMetadata.externalId is required when sourceMetadata is provided", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             if (sm.getSource() == null || sm.getSource().isBlank()) {
                 return EvaluationOutcome.fail("sourceMetadata.source is required when sourceMetadata is provided", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // Breed and location are optional, but if present ensure not blank
         if (entity.getBreed() != null && entity.getBreed().isBlank()) {
             return EvaluationOutcome.fail("breed, if provided, must not be blank", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (entity.getLocation() != null && entity.getLocation().isBlank()) {
             return EvaluationOutcome.fail("location, if provided, must not be blank", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // If all checks pass
         return EvaluationOutcome.success();
    }
}