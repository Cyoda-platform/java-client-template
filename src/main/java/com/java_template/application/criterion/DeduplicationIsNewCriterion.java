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
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Laureate.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // Must use exact criterion name match
        return "DeduplicationIsNewCriterion".equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Laureate> context) {
         Laureate entity = context.entity();

         // Validate presence of natural key fields required to determine uniqueness
         if (entity.getId() == null) {
            logger.debug("Deduplication check failed: missing id");
            return EvaluationOutcome.fail("Laureate source id is required for deduplication", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getFirstname() == null || entity.getFirstname().isBlank()) {
            logger.debug("Deduplication check failed: missing firstname for id={}", entity.getId());
            return EvaluationOutcome.fail("Laureate firstname is required for deduplication", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getSurname() == null || entity.getSurname().isBlank()) {
            logger.debug("Deduplication check failed: missing surname for id={}", entity.getId());
            return EvaluationOutcome.fail("Laureate surname is required for deduplication", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getYear() == null || entity.getYear().isBlank()) {
            logger.debug("Deduplication check failed: missing year for id={}", entity.getId());
            return EvaluationOutcome.fail("Laureate award year is required for deduplication", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getCategory() == null || entity.getCategory().isBlank()) {
            logger.debug("Deduplication check failed: missing category for id={}", entity.getId());
            return EvaluationOutcome.fail("Laureate category is required for deduplication", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String sourceJobId = entity.getSourceJobId();
         String createdAt = entity.getCreatedAt();

         // Heuristic to decide whether this laureate should be considered NEW.
         // - If both sourceJobId and createdAt are present -> treat as NEW (produced by current ingestion)
         // - If one is present and the other missing -> treat as data/business issue and fail with appropriate category
         // - If both absent -> cannot determine NEW here (defer to downstream deduplication that may consult storage)
         if (sourceJobId != null && !sourceJobId.isBlank() && createdAt != null && !createdAt.isBlank()) {
             logger.debug("Laureate id={} marked as NEW (sourceJobId and createdAt present)", entity.getId());
             return EvaluationOutcome.success();
         }

         if (sourceJobId != null && !sourceJobId.isBlank() && (createdAt == null || createdAt.isBlank())) {
             logger.warn("Laureate id={} has sourceJobId but missing createdAt - data quality issue", entity.getId());
             return EvaluationOutcome.fail("Laureate has sourceJobId but createdAt is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         if ((sourceJobId == null || sourceJobId.isBlank()) && createdAt != null && !createdAt.isBlank()) {
             logger.warn("Laureate id={} has createdAt but missing sourceJobId - business rule violated", entity.getId());
             return EvaluationOutcome.fail("Laureate has createdAt but missing sourceJobId; cannot mark as NEW", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Both absent -> unable to determine NEW based solely on payload
         logger.debug("Laureate id={} missing both sourceJobId and createdAt; cannot determine NEW status", entity.getId());
         return EvaluationOutcome.fail(
             "Unable to determine NEW status for laureate; missing sourceJobId and createdAt",
             StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
         );
    }
}