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
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(DataFeed.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<DataFeed> context) {
         DataFeed entity = context.entity();

         if (entity == null) {
             return EvaluationOutcome.fail("DataFeed entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // At fetch-complete stage we expect the feed to be in FETCHING state (fetch started)
         if (entity.getStatus() == null || entity.getStatus().isBlank()) {
             return EvaluationOutcome.fail("DataFeed.status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (!"FETCHING".equals(entity.getStatus())) {
             return EvaluationOutcome.fail("DataFeed status must be 'FETCHING' for fetch completion check", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         if (entity.getLastFetchedAt() == null || entity.getLastFetchedAt().isBlank()) {
             return EvaluationOutcome.fail("lastFetchedAt is required to mark fetch complete", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (entity.getLastChecksum() == null || entity.getLastChecksum().isBlank()) {
             return EvaluationOutcome.fail("lastChecksum is required to mark fetch complete", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (entity.getRecordCount() == null) {
             return EvaluationOutcome.fail("recordCount is required to mark fetch complete", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (entity.getRecordCount() < 0) {
             return EvaluationOutcome.fail("recordCount must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // If all required fetch attributes are present, consider fetch complete.
         return EvaluationOutcome.success();
    }
}