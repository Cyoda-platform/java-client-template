package com.java_template.application.criterion;

import com.java_template.application.entity.datafeed.version_1.DataFeed;
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
public class FetchCompleteCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public FetchCompleteCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(DataFeed.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // Must match the exact criterion class name
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<DataFeed> context) {
         DataFeed entity = context.entity();

         if (entity == null) {
             logger.warn("FetchCompleteCriterion - missing entity in context");
             return EvaluationOutcome.fail("DataFeed entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Basic required identity fields (use only existing getters)
         if (entity.getId() == null || entity.getId().isBlank()) {
             return EvaluationOutcome.fail("DataFeed.id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (entity.getUrl() == null || entity.getUrl().isBlank()) {
             return EvaluationOutcome.fail("DataFeed.url is required to perform fetch", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // At fetch-complete stage we expect the feed to be in FETCHING state (fetch started)
         if (entity.getStatus() == null || entity.getStatus().isBlank()) {
             return EvaluationOutcome.fail("DataFeed.status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (!"FETCHING".equals(entity.getStatus())) {
             return EvaluationOutcome.fail("DataFeed status must be 'FETCHING' for fetch completion check", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Timestamps and checksum produced by the fetch process
         if (entity.getLastFetchedAt() == null || entity.getLastFetchedAt().isBlank()) {
             return EvaluationOutcome.fail("lastFetchedAt is required to mark fetch complete", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (entity.getLastChecksum() == null || entity.getLastChecksum().isBlank()) {
             return EvaluationOutcome.fail("lastChecksum is required to mark fetch complete", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Row count must be present and non-negative
         if (entity.getRecordCount() == null) {
             return EvaluationOutcome.fail("recordCount is required to mark fetch complete", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getRecordCount() < 0) {
             return EvaluationOutcome.fail("recordCount must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Minimal schema preview should be available after fetch to proceed to validation stage
         if (entity.getSchemaPreview() == null || entity.getSchemaPreview().isEmpty()) {
             return EvaluationOutcome.fail("schemaPreview must contain at least one detected column type", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // All checks passed -> fetch can be considered complete
         logger.debug("FetchCompleteCriterion succeeded for DataFeed id={}", entity.getId());
         return EvaluationOutcome.success();
    }
}