package com.java_template.application.criterion;

import com.java_template.application.entity.ingestjob.version_1.IngestJob;
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
public class PersistFailCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public PersistFailCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(IngestJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // supports must match the exact criterion name
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<IngestJob> context) {
         IngestJob entity = context.entity();

         // If a stored item technical id is present, persistence succeeded => success
         if (entity.getStoredItemTechnicalId() != null && !entity.getStoredItemTechnicalId().isBlank()) {
             logger.debug("IngestJob {}: storedItemTechnicalId present -> persistence succeeded", entity.getTechnicalId());
             return EvaluationOutcome.success();
         }

         // No stored id => persistence did not complete successfully
         String status = entity.getStatus();
         String error = entity.getErrorMessage();

         // If job explicitly marked as FAILED, ensure there is an error message and report data quality failure
         if (status != null && status.equalsIgnoreCase("FAILED")) {
             if (error == null || error.isBlank()) {
                 logger.warn("IngestJob {} is FAILED but missing errorMessage", entity.getTechnicalId());
                 return EvaluationOutcome.fail("Persist failed but no error message provided", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             } else {
                 logger.warn("IngestJob {} persistence failed: {}", entity.getTechnicalId(), error);
                 return EvaluationOutcome.fail("Persist failed: " + error, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // If job is marked COMPLETED but no stored id -> business rule violation
         if (status != null && status.equalsIgnoreCase("COMPLETED")) {
             logger.error("IngestJob {} marked COMPLETED but storedItemTechnicalId is missing", entity.getTechnicalId());
             return EvaluationOutcome.fail("Job marked COMPLETED but stored item id missing", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Generic: persistence hasn't produced a stored id and job not explicitly failed -> treat as business rule failure
         logger.info("IngestJob {}: persistence incomplete (status={}, storedItemTechnicalId=null)", entity.getTechnicalId(), status);
         return EvaluationOutcome.fail("Persist did not complete and no stored item id assigned", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }
}