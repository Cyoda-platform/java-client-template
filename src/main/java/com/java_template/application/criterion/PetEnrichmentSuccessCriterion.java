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
public class PetEnrichmentSuccessCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public PetEnrichmentSuccessCriterion(SerializerFactory serializerFactory) {
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
             logger.warn("Pet entity is null in context");
             return EvaluationOutcome.fail("Pet entity is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String status = entity.getStatus();
         if (status == null || status.trim().isEmpty()) {
             logger.warn("Pet {} missing status", entity.getId());
             return EvaluationOutcome.fail("Pet status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Enrichment is considered successful when pet.status == "available"
         if ("available".equalsIgnoreCase(status)) {
             // Ensure enrichment produced expected quality artifacts: breed normalized and accessible photos
             String breed = entity.getBreed();
             if (breed == null || breed.trim().isEmpty()) {
                 logger.info("Pet {} marked available but breed is missing or empty", entity.getId());
                 return EvaluationOutcome.fail("Breed missing after enrichment", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }

             List<String> photos = entity.getPhotos();
             if (photos == null || photos.isEmpty()) {
                 logger.info("Pet {} marked available but photos are missing", entity.getId());
                 return EvaluationOutcome.fail("No photos available after enrichment", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }

             // Basic sanity: name and species should exist (validated earlier in pipeline, but double-check)
             if (entity.getName() == null || entity.getName().trim().isEmpty()) {
                 logger.info("Pet {} available but name is missing", entity.getId());
                 return EvaluationOutcome.fail("Pet name missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
             if (entity.getSpecies() == null || entity.getSpecies().trim().isEmpty()) {
                 logger.info("Pet {} available but species is missing", entity.getId());
                 return EvaluationOutcome.fail("Pet species missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }

             return EvaluationOutcome.success();
         }

         // If enrichment hasn't run yet but pet is validated, indicate enrichment pending
         if ("validated".equalsIgnoreCase(status)) {
             logger.info("Pet {} is validated but enrichment not completed", entity.getId());
             return EvaluationOutcome.fail("Enrichment not completed: pet is still validated", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // If enrichment failed earlier
         if ("validation_failed".equalsIgnoreCase(status)) {
             logger.info("Pet {} enrichment/validation previously failed", entity.getId());
             return EvaluationOutcome.fail("Enrichment/validation previously failed", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // For other terminal or intermediate statuses, treat as business rule not satisfied for enrichment success
         logger.info("Pet {} in status {} which does not indicate enrichment success", entity.getId(), status);
         return EvaluationOutcome.fail("Unexpected pet status for enrichment success: " + status, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }
}