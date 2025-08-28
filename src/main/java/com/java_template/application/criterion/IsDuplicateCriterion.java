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
public class IsDuplicateCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public IsDuplicateCriterion(SerializerFactory serializerFactory) {
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
        // must match the exact criterion name (case-sensitive)
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Laureate> context) {
         Laureate entity = context.entity();

         // Ensure required identity fields are present before attempting duplicate detection.
         if (entity.getId() == null) {
             logger.debug("Laureate id missing during duplicate check");
             return EvaluationOutcome.fail("Laureate id is required for duplicate detection", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getFirstname() == null || entity.getFirstname().isBlank()) {
             logger.debug("Laureate firstname missing during duplicate check (id={})", entity.getId());
             return EvaluationOutcome.fail("Laureate firstname is required for duplicate detection", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getSurname() == null || entity.getSurname().isBlank()) {
             logger.debug("Laureate surname missing during duplicate check (id={})", entity.getId());
             return EvaluationOutcome.fail("Laureate surname is required for duplicate detection", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         /*
          Duplicate detection heuristic:
          - If the incoming laureate contains a sourceSnapshot and a lastUpdatedAt timestamp,
            we treat this as evidence that the record was previously seen (duplicate).
          - If either sourceSnapshot or lastUpdatedAt is missing/blank we consider it a new record (not a duplicate).
          Rationale: we must rely only on available entity properties (no external DB access here).
         */
         boolean hasSnapshot = entity.getSourceSnapshot() != null && !entity.getSourceSnapshot().isBlank();
         boolean hasLastUpdated = entity.getLastUpdatedAt() != null && !entity.getLastUpdatedAt().isBlank();

         if (!hasSnapshot || !hasLastUpdated) {
             // Not enough evidence to consider this a duplicate -> criterion fails (i.e., "IsDuplicate" == false)
             String reason = String.format("Insufficient evidence for duplicate (sourceSnapshot=%s, lastUpdatedAt=%s)",
                     hasSnapshot ? "present" : "missing",
                     hasLastUpdated ? "present" : "missing");
             logger.debug("Laureate considered new record: {} (id={})", reason, entity.getId());
             return EvaluationOutcome.fail("No prior snapshot/timestamp found — treat as new record", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Both pieces present: treat as duplicate
         logger.debug("Laureate considered duplicate (id={})", entity.getId());
         return EvaluationOutcome.success();
    }
}