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
public class NoMatchCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public NoMatchCriterion(SerializerFactory serializerFactory) {
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
        // Must use exact criterion name (case-sensitive)
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Laureate> context) {
         Laureate entity = context.entity();
         if (entity == null) {
             logger.warn("NoMatchCriterion: incoming Laureate entity is null");
             return EvaluationOutcome.fail("Entity payload is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Required validation: essential identity & classification fields
         if (entity.getExternalId() == null || entity.getExternalId().isBlank()) {
             logger.debug("NoMatchCriterion: externalId is missing or blank");
             return EvaluationOutcome.fail("externalId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getFullName() == null || entity.getFullName().isBlank()) {
             logger.debug("NoMatchCriterion: fullName is missing or blank");
             return EvaluationOutcome.fail("fullName is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getPrizeCategory() == null || entity.getPrizeCategory().isBlank()) {
             logger.debug("NoMatchCriterion: prizeCategory is missing or blank");
             return EvaluationOutcome.fail("prizeCategory is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getPrizeYear() == null) {
             logger.debug("NoMatchCriterion: prizeYear is missing");
             return EvaluationOutcome.fail("prizeYear is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getPrizeYear() <= 0) {
             logger.debug("NoMatchCriterion: prizeYear has invalid value: {}", entity.getPrizeYear());
             return EvaluationOutcome.fail("prizeYear must be a positive integer", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Data quality checks for timestamps and raw payload for new records
         if (entity.getFirstSeenTimestamp() == null || entity.getFirstSeenTimestamp().isBlank()) {
             logger.debug("NoMatchCriterion: firstSeenTimestamp missing for externalId={}", entity.getExternalId());
             return EvaluationOutcome.fail("firstSeenTimestamp is required for new records", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (entity.getLastSeenTimestamp() == null || entity.getLastSeenTimestamp().isBlank()) {
             logger.debug("NoMatchCriterion: lastSeenTimestamp missing for externalId={}", entity.getExternalId());
             return EvaluationOutcome.fail("lastSeenTimestamp is required for new records", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // rawPayload is recommended to keep an audit of the source, treat missing as data-quality failure
         if (entity.getRawPayload() == null || entity.getRawPayload().isBlank()) {
             logger.debug("NoMatchCriterion: rawPayload missing for externalId={}", entity.getExternalId());
             return EvaluationOutcome.fail("rawPayload is required for incoming laureate payloads", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Basic business rule: ensure changeSummary consistent for a newly created record (may be empty for initial import)
         // No strict rule here, just pass — richer merge decisions happen in Merge/Update processors.
         logger.debug("NoMatchCriterion: Laureate passed validation checks externalId={}", entity.getExternalId());
         return EvaluationOutcome.success();
    }
}