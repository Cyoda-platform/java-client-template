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
public class ValidPetDataCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ValidPetDataCriterion(SerializerFactory serializerFactory) {
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
             logger.warn("Pet entity is null in ValidPetDataCriterion");
             return EvaluationOutcome.fail("Pet entity is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required identity and descriptive fields
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

         // Data quality: ageMonths must be non-negative if present
         if (entity.getAgeMonths() != null && entity.getAgeMonths() < 0) {
             return EvaluationOutcome.fail("ageMonths must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Metadata consistency checks
         Pet.Metadata md = entity.getMetadata();
         if (md != null) {
             if (md.getImages() == null) {
                 return EvaluationOutcome.fail("metadata.images must be present (may be empty)", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             if (md.getTags() == null) {
                 return EvaluationOutcome.fail("metadata.tags must be present (may be empty)", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // Business rule: for common domestic species require a breed to help matching/indexing
         String species = entity.getSpecies();
         if (species != null) {
             String speciesLower = species.trim().toLowerCase();
             if (speciesLower.equals("dog") || speciesLower.equals("cat")) {
                 if (entity.getBreed() == null || entity.getBreed().isBlank()) {
                     return EvaluationOutcome.fail("breed is required for dogs and cats", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
                 }
             }
         }

         return EvaluationOutcome.success();
    }
}