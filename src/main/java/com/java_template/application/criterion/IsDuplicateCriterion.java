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
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Laureate.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Laureate> context) {
         Laureate entity = context.entity();

         // Basic required fields validation
         if (entity.getId() == null) {
             return EvaluationOutcome.fail("Laureate id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getFirstname() == null || entity.getFirstname().isBlank()) {
             return EvaluationOutcome.fail("Laureate firstname is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getSurname() == null || entity.getSurname().isBlank()) {
             return EvaluationOutcome.fail("Laureate surname is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getYear() == null || entity.getYear().isBlank()) {
             return EvaluationOutcome.fail("Laureate year is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getCategory() == null || entity.getCategory().isBlank()) {
             return EvaluationOutcome.fail("Laureate category is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         /*
          Business rule for duplicate detection (heuristic):
          - If we have a persisted snapshot (sourceSnapshot) and a lastUpdatedAt timestamp,
            we assume this record has been seen before and treat it as a duplicate.
          - If either of these pieces is missing, consider it a new record (not a duplicate).
          This implements a deterministic, reproducible check using only the entity's available fields.
         */
         if (entity.getSourceSnapshot() == null || entity.getSourceSnapshot().isBlank()) {
             return EvaluationOutcome.fail("No source snapshot available — treat as new record", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (entity.getLastUpdatedAt() == null || entity.getLastUpdatedAt().isBlank()) {
             return EvaluationOutcome.fail("No lastUpdatedAt timestamp — treat as new record", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // If we reach here, heuristics indicate the incoming record matches an existing snapshot/timestamp -> duplicate
         return EvaluationOutcome.success();
    }
}