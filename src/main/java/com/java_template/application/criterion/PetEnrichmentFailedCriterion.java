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

import java.util.Collection;
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
             logger.warn("Pet entity is null in context");
             return EvaluationOutcome.fail("Pet entity missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String status = pet.getStatus();
         // If enrichment explicitly failed and status reflects validation failure, flag as enrichment failure.
         if ("validation_failed".equalsIgnoreCase(status)) {
             return EvaluationOutcome.fail("Pet enrichment failed (status=validation_failed)", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // If pet remains in 'validated' state but has no photos (likely enrichment step did not complete or photo validation failed),
         // treat as enrichment failure candidate.
         if ("validated".equalsIgnoreCase(status)) {
             List<String> photosList = null;
             Object photosObj = null;
             try {
                 photosObj = pet.getPhotos();
             } catch (Throwable t) {
                 // Defensive: if getter exists but unexpected type/exception, log and continue to a generic pass.
                 logger.debug("Unable to read photos from pet {}", pet, t);
             }

             boolean hasPhotos = false;
             if (photosObj instanceof Collection) {
                 hasPhotos = !((Collection<?>) photosObj).isEmpty();
             } else if (photosObj instanceof Object[]) {
                 hasPhotos = ((Object[]) photosObj).length > 0;
             } else if (photosObj instanceof List) {
                 hasPhotos = !((List<?>) photosObj).isEmpty();
             } else if (photosObj != null) {
                 // Unknown but non-null photos value — consider it present.
                 hasPhotos = true;
             }

             if (!hasPhotos) {
                 return EvaluationOutcome.fail("Pet enrichment likely failed: no accessible photos after enrichment", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // No enrichment failure detected.
         return EvaluationOutcome.success();
    }
}