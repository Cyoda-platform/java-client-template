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
        return serializer.withRequest(request)
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
             return EvaluationOutcome.fail("Pet entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required fields
         String name = pet.getName();
         if (name == null || name.trim().isEmpty()) {
             return EvaluationOutcome.fail("Pet name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String species = pet.getSpecies();
         if (species == null || species.trim().isEmpty()) {
             return EvaluationOutcome.fail("Pet species is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Enrichment should result in status = "available"
         String status = pet.getStatus();
         if (status == null || !"available".equalsIgnoreCase(status)) {
             return EvaluationOutcome.fail("Pet is not marked as 'available' after enrichment", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Photos must be present after enrichment
         Object photos = null;
         try {
             photos = pet.getPhotos();
         } catch (Exception e) {
             logger.debug("Unable to read photos property on Pet {}", pet.getId(), e);
         }

         boolean hasPhotos = false;
         if (photos != null) {
             if (photos instanceof java.util.Collection) {
                 hasPhotos = !((java.util.Collection<?>) photos).isEmpty();
             } else if (photos.getClass().isArray()) {
                 hasPhotos = java.lang.reflect.Array.getLength(photos) > 0;
             }
         }

         if (!hasPhotos) {
             return EvaluationOutcome.fail("No photos present after enrichment", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         return EvaluationOutcome.success();
    }
}