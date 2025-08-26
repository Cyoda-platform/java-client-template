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
public class ValidationFailedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ValidationFailedCriterion(SerializerFactory serializerFactory) {
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
             logger.warn("Pet entity is null in ValidationFailedCriterion");
             return EvaluationOutcome.fail("Pet entity missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required string fields
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
         if (entity.getCreatedAt() == null || entity.getCreatedAt().isBlank()) {
             return EvaluationOutcome.fail("createdAt is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getUpdatedAt() == null || entity.getUpdatedAt().isBlank()) {
             return EvaluationOutcome.fail("updatedAt is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Optional strings must not be blank if present
         if (entity.getSourceId() != null && entity.getSourceId().isBlank()) {
             return EvaluationOutcome.fail("sourceId, if present, must not be blank", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getSourceUpdatedAt() != null && entity.getSourceUpdatedAt().isBlank()) {
             return EvaluationOutcome.fail("sourceUpdatedAt, if present, must not be blank", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getDescription() != null && entity.getDescription().isBlank()) {
             return EvaluationOutcome.fail("description, if present, must not be blank", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getBreed() != null && entity.getBreed().isBlank()) {
             return EvaluationOutcome.fail("breed, if present, must not be blank", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Age sanity check
         if (entity.getAge() != null && entity.getAge() < 0) {
             return EvaluationOutcome.fail("age must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Collections should not contain blank entries
         List<String> images = entity.getImages();
         if (images != null) {
             for (String img : images) {
                 if (img == null || img.isBlank()) {
                     return EvaluationOutcome.fail("images must not contain blank entries", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
             }
         }

         List<String> tags = entity.getTags();
         if (tags != null) {
             for (String tag : tags) {
                 if (tag == null || tag.isBlank()) {
                     return EvaluationOutcome.fail("tags must not contain blank entries", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
             }
         }

         // Basic business rule: createdAt must not be after updatedAt (best-effort string compare: ISO strings expected)
         String createdAt = entity.getCreatedAt();
         String updatedAt = entity.getUpdatedAt();
         if (createdAt != null && updatedAt != null && createdAt.compareTo(updatedAt) > 0) {
             return EvaluationOutcome.fail("createdAt cannot be after updatedAt", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

        return EvaluationOutcome.success();
    }
}