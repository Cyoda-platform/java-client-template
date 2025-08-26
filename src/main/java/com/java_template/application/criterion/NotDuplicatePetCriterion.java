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
public class NotDuplicatePetCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public NotDuplicatePetCriterion(SerializerFactory serializerFactory) {
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
        // Use exact criterion name match (case-sensitive) as required.
        if (modelSpec == null || modelSpec.operationName() == null) return false;
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Pet> context) {
         Pet entity = context.entity();
         if (entity == null) {
             logger.warn("Validation failed: pet entity is null");
             return EvaluationOutcome.fail("Pet entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // If a source petstore id is present, we can reasonably assume the source provides a unique identifier
         if (entity.getSourceMetadata() != null) {
             String sourceId = entity.getSourceMetadata().getPetstoreId();
             if (sourceId != null && !sourceId.isBlank()) {
                 logger.debug("Pet has external source id '{}', treating as unique", sourceId);
                 // Presence of an external source id is treated as a strong uniqueness signal
                 return EvaluationOutcome.success();
             }
         }

         // Without an external id, rely on a simple fingerprint composed of required identity fields.
         // We require both name and breed to be present to have a reasonable chance to detect duplicates downstream.
         String name = entity.getName();
         String breed = entity.getBreed();

         if (name == null || name.isBlank() || breed == null || breed.isBlank()) {
             // We cannot reliably exclude duplicates if key identity fields are missing.
             logger.warn("Insufficient identity information for pet (name='{}', breed='{}') and no external id present", name, breed);
             return EvaluationOutcome.fail(
                 "Insufficient identity information (name and breed are required when no source id) to rule out duplicates",
                 StandardEvalReasonCategories.DATA_QUALITY_FAILURE
             );
         }

         // Heuristic: if both name and breed are provided, consider the record non-duplicate at this evaluation stage.
         // Exact duplicate detection should be performed by a downstream service that can query the datastore.
         logger.debug("Pet provided with name='{}' and breed='{}' and no external id; passing criterion for now", name, breed);
         return EvaluationOutcome.success();
    }
}