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
public class PetValidDataCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public PetValidDataCriterion(SerializerFactory serializerFactory) {
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
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Pet> context) {
         Pet entity = context.entity();

         if (entity == null) {
             return EvaluationOutcome.fail("Pet entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required fields: name, species, status
         if (entity.getName() == null || entity.getName().isBlank()) {
             return EvaluationOutcome.fail("name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (entity.getSpecies() == null || entity.getSpecies().isBlank()) {
             return EvaluationOutcome.fail("species is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (entity.getStatus() == null || entity.getStatus().isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Age must be non-negative if provided
         Integer age = entity.getAge();
         if (age != null && age < 0) {
             return EvaluationOutcome.fail("age cannot be negative", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Photos: entries must not be blank; warn/get flagged if none provided (data quality)
         List<String> photos = entity.getPhotos();
         if (photos == null) {
             return EvaluationOutcome.fail("photos list missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         } else {
             if (photos.isEmpty()) {
                 return EvaluationOutcome.fail("no photos provided for pet (recommended)", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             for (String p : photos) {
                 if (p == null || p.isBlank()) {
                     return EvaluationOutcome.fail("photos must not contain blank entries", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
             }
         }

         // Optional string fields: if present they must not be blank (data quality)
         if (entity.getBreed() != null && entity.getBreed().isBlank()) {
             return EvaluationOutcome.fail("breed, if provided, must not be blank", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (entity.getDescription() != null && entity.getDescription().isBlank()) {
             return EvaluationOutcome.fail("description, if provided, must not be blank", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (entity.getSourceId() != null && entity.getSourceId().isBlank()) {
             return EvaluationOutcome.fail("sourceId, if provided, must not be blank", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (entity.getSourceUrl() != null && entity.getSourceUrl().isBlank()) {
             return EvaluationOutcome.fail("sourceUrl, if provided, must not be blank", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Business rule example: if sourceId provided then sourceUrl should ideally be present (data completeness)
         if (entity.getSourceId() != null && (entity.getSourceUrl() == null || entity.getSourceUrl().isBlank())) {
             return EvaluationOutcome.fail("sourceId is present but sourceUrl is missing", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

        return EvaluationOutcome.success();
    }
}