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
public class EnrichmentCompleteCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public EnrichmentCompleteCriterion(SerializerFactory serializerFactory) {
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
             logger.warn("EnrichmentCompleteCriterion invoked with null entity");
             return EvaluationOutcome.fail("Pet entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Metadata must be present to consider enrichment complete
         Pet.Metadata metadata = entity.getMetadata();
         if (metadata == null) {
             logger.debug("Pet [{}] missing metadata; enrichment not performed", entity.getId());
             return EvaluationOutcome.fail("enrichment metadata missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // enrichedAt timestamp must be present and non-blank
         String enrichedAt = metadata.getEnrichedAt();
         if (enrichedAt == null || enrichedAt.isBlank()) {
             logger.debug("Pet [{}] metadata.enrichedAt missing or blank; enrichment incomplete", entity.getId());
             return EvaluationOutcome.fail("enrichedAt timestamp missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // At least one enrichment artifact should exist: images or tags
         boolean hasImages = metadata.getImages() != null && !metadata.getImages().isEmpty();
         boolean hasTags = metadata.getTags() != null && !metadata.getTags().isEmpty();
         if (!hasImages && !hasTags) {
             logger.debug("Pet [{}] has no enrichment artifacts (images/tags)", entity.getId());
             return EvaluationOutcome.fail("no enrichment artifacts (images or tags) found", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // If we reach here, enrichment appears complete
         return EvaluationOutcome.success();
    }
}