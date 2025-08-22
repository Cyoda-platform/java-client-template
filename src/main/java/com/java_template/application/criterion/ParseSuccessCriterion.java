package com.java_template.application.criterion;

import com.java_template.application.entity.petsyncjob.version_1.PetSyncJob;
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
public class ParseSuccessCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ParseSuccessCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(PetSyncJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<PetSyncJob> context) {
         PetSyncJob entity = context.entity();

         if (entity == null) {
             return EvaluationOutcome.fail("PetSyncJob entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Basic required fields for parse-to-persist transition
         if (entity.getStatus() == null || entity.getStatus().isBlank()) {
             return EvaluationOutcome.fail("Job status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // ParseSuccessCriterion expects the job to be in PARSING state before moving to PERSISTING
         if (!"parsing".equalsIgnoreCase(entity.getStatus())) {
             return EvaluationOutcome.fail("Job status must be 'parsing' to proceed to persist", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Config must be present (parser depends on config like sourceUrl)
         if (entity.getConfig() == null) {
             return EvaluationOutcome.fail("Job config is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // fetchedCount should be set by the parsing process (can be zero but must not be null/negative)
         Integer fetched = entity.getFetchedCount();
         if (fetched == null) {
             return EvaluationOutcome.fail("fetchedCount is not set by parser", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (fetched < 0) {
             return EvaluationOutcome.fail("fetchedCount is invalid (negative)", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Any error message indicates parsing problems
         if (entity.getErrorMessage() != null && !entity.getErrorMessage().isBlank()) {
             return EvaluationOutcome.fail("Parsing produced error: " + entity.getErrorMessage(), StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // startTime and source are expected to be present for a valid job execution context
         if (entity.getStartTime() == null || entity.getStartTime().isBlank()) {
             return EvaluationOutcome.fail("startTime is required for job lifecycle", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getSource() == null || entity.getSource().isBlank()) {
             return EvaluationOutcome.fail("source is required for job lifecycle", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         return EvaluationOutcome.success();
    }
}