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
public class DeduplicationIsNewCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public DeduplicationIsNewCriterion(SerializerFactory serializerFactory) {
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
         // Validate presence of natural key fields required to determine uniqueness
         if (entity.getId() == null) {
            return EvaluationOutcome.fail("Laureate source id is required for deduplication", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getFirstname() == null || entity.getFirstname().isBlank()) {
            return EvaluationOutcome.fail("Laureate firstname is required for deduplication", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getSurname() == null || entity.getSurname().isBlank()) {
            return EvaluationOutcome.fail("Laureate surname is required for deduplication", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getYear() == null || entity.getYear().isBlank()) {
            return EvaluationOutcome.fail("Laureate award year is required for deduplication", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getCategory() == null || entity.getCategory().isBlank()) {
            return EvaluationOutcome.fail("Laureate category is required for deduplication", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         /*
          * Heuristic to decide whether this laureate should be considered NEW.
          * - If this record already carries a sourceJobId and createdAt it indicates it was produced
          *   by the current ingestion and should be treated as NEW for persistence.
          * - If those fields are absent we cannot confidently mark it as NEW here (it may be a duplicate),
          *   therefore fail the criterion so downstream deduplication/persist logic can handle it explicitly.
          *
          * Note: This criterion intentionally avoids querying external storage and relies only on the
          * entity payload supplied to the criterion (per constraints).
          */
         if (entity.getSourceJobId() != null && !entity.getSourceJobId().isBlank()
             && entity.getCreatedAt() != null && !entity.getCreatedAt().isBlank()) {
             return EvaluationOutcome.success();
         }

         // Unable to mark as NEW based on provided data
         return EvaluationOutcome.fail(
             "Unable to determine NEW status for laureate; missing sourceJobId or createdAt",
             StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
         );
    }
}