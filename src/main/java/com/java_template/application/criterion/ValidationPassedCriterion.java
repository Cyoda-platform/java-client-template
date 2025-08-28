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
public class ValidationPassedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ValidationPassedCriterion(SerializerFactory serializerFactory) {
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
             logger.debug("Validation failed: entity is null");
             return EvaluationOutcome.fail("Entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required string fields: petId, name, species, status
         if (entity.getPetId() == null || entity.getPetId().isBlank()) {
             return EvaluationOutcome.fail("petId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
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

         // Age, if present, must be non-negative
         if (entity.getAge() != null && entity.getAge() < 0) {
             return EvaluationOutcome.fail("age must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // photoUrls must be present (non-null) and contain no blank entries; require at least one URL
         List<String> photoUrls = entity.getPhotoUrls();
         if (photoUrls == null || photoUrls.isEmpty()) {
             return EvaluationOutcome.fail("photoUrls must be provided and contain at least one URL", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         for (String url : photoUrls) {
             if (url == null || url.isBlank()) {
                 return EvaluationOutcome.fail("photoUrls contains blank or null entry", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // tags must be present (non-null) and contain no blank entries
         List<String> tags = entity.getTags();
         if (tags == null) {
             return EvaluationOutcome.fail("tags collection must be present (may be empty)", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         for (String t : tags) {
             if (t == null || t.isBlank()) {
                 return EvaluationOutcome.fail("tags contains blank or null entry", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

        return EvaluationOutcome.success();
    }
}