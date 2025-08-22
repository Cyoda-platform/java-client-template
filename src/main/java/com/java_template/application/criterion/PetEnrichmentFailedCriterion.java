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
public class PetEnrichmentFailedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public PetEnrichmentFailedCriterion(SerializerFactory serializerFactory) {
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
             logger.debug("PetEnrichmentFailedCriterion: entity is null in context");
             return EvaluationOutcome.success();
         }

         String petId = safeString(pet.getId());
         String status = safeString(pet.getStatus());

         // If the pet is already marked as validation_failed, consider enrichment failed.
         if ("validation_failed".equalsIgnoreCase(status)) {
             String msg = String.format("Pet enrichment failed for petId=%s: status=%s", petId, status);
             logger.info(msg);
             return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // If pet is in validated state, ensure photos are reasonable for enrichment.
         if ("validated".equalsIgnoreCase(status)) {
             List<String> photos = pet.getPhotos();
             if (photos == null || photos.isEmpty()) {
                 String msg = String.format("Pet enrichment likely to fail for petId=%s: no photos available for enrichment", petId);
                 logger.info(msg);
                 return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }

             // If any photo URL looks malformed, flag as enrichment failure risk.
             for (String url : photos) {
                 if (!isPhotoUrlWellFormed(url)) {
                     String msg = String.format("Pet enrichment likely to fail for petId=%s: invalid photo URL detected (%s)", petId, url);
                     logger.info(msg);
                     return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
             }
         }

         // Otherwise, no enrichment failure detected by this criterion.
         return EvaluationOutcome.success();
    }

    private boolean isPhotoUrlWellFormed(String url) {
        if (url == null) return false;
        String trimmed = url.trim();
        if (trimmed.isEmpty()) return false;
        String lower = trimmed.toLowerCase();
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    private String safeString(String s) {
        return s == null ? "" : s;
    }
}