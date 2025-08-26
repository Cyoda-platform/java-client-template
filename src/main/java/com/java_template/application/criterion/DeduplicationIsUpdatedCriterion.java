package com.java_template.application.criterion;

import com.java_template.application.entity.laureate.version_1.Laureate;
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
public class DeduplicationIsUpdatedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public DeduplicationIsUpdatedCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Laureate.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // Must match the exact criterion name (case-sensitive)
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Laureate> context) {
         Laureate entity = context.entity();
         // Basic prerequisite: id must be present for deduplication decisions
         if (entity.getId() == null) {
            logger.debug("DeduplicationIsUpdatedCriterion: missing id on entity");
            return EvaluationOutcome.fail("Laureate.id is required for deduplication", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Interpret "UPDATED" as candidate that has been associated with an existing persisted record:
         // For our flows we expect an update candidate to carry a sourceJobId (indicating an import context)
         // and a createdAt timestamp (indicating it came from a persisted record previously).
         String sourceJobId = entity.getSourceJobId();
         String createdAt = entity.getCreatedAt();

         boolean hasSourceJob = sourceJobId != null && !sourceJobId.isBlank();
         boolean hasCreatedAt = createdAt != null && !createdAt.isBlank();

         if (hasSourceJob && hasCreatedAt) {
             logger.debug("DeduplicationIsUpdatedCriterion: entity {} identified as UPDATED (sourceJobId and createdAt present)", entity.getId());
             return EvaluationOutcome.success();
         }

         // If not marked as updated, fail the criterion so the deduplication flow can route to other outcomes (NEW/DUPLICATE).
         logger.debug("DeduplicationIsUpdatedCriterion: entity {} not identified as UPDATED (sourceJobId present: {}, createdAt present: {})", entity.getId(), hasSourceJob, hasCreatedAt);
         return EvaluationOutcome.fail("Laureate not identified as UPDATED (missing sourceJobId or createdAt)", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }
}