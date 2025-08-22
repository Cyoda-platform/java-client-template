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
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(IngestJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<IngestJob> context) {
         IngestJob entity = context.entity();
         if (entity == null) {
             logger.warn("PersistFailCriterion invoked with null entity");
             return EvaluationOutcome.fail("Entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String status = entity.getStatus();
         // This criterion signals that persistence has failed. It should succeed when the job is in FAILED state
         // with a present errorMessage and without a storedItemTechnicalId.
         if (status == null || status.isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (!"FAILED".equalsIgnoreCase(status)) {
             // Not a persistence failure condition
             return EvaluationOutcome.fail("Job is not in FAILED state", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // status == FAILED: validate failure semantics
         String err = entity.getErrorMessage();
         String storedId = entity.getStoredItemTechnicalId();

         if (err == null || err.isBlank()) {
             return EvaluationOutcome.fail("Persist failed but errorMessage is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         if (storedId != null && !storedId.isBlank()) {
             return EvaluationOutcome.fail("Persist failed but storedItemTechnicalId is present", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // All checks pass: this is a valid persist failure
         return EvaluationOutcome.success();
    }
}