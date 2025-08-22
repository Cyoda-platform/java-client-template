package com.java_template.application.criterion;

import com.java_template.application.entity.petenrichmentjob.version_1.PetEnrichmentJob;
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
public class FetchFailureCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String CRITERION_NAME = "FetchFailureCriterion";

    public FetchFailureCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(PetEnrichmentJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // Must use exact criterion name
        return CRITERION_NAME.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<PetEnrichmentJob> context) {
         PetEnrichmentJob entity = context.entity();

         if (entity == null) {
             logger.debug("FetchFailureCriterion invoked with null entity");
             return EvaluationOutcome.fail("PetEnrichmentJob entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String id = entity.getJobId() != null ? entity.getJobId() : "<unknown-jobId>";

         // If job was explicitly marked FAILED -> criterion triggers fail
         if (entity.getStatus() != null && entity.getStatus().equalsIgnoreCase("FAILED")) {
             logger.info("PetEnrichmentJob {} status is FAILED", id);
             return EvaluationOutcome.fail("Fetch operation marked FAILED for job " + id, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // If there are reported errors -> consider data quality failure
         if (entity.getErrors() != null && !entity.getErrors().isEmpty()) {
             String firstError = entity.getErrors().get(0);
             logger.info("PetEnrichmentJob {} reported errors during fetch: {}", id, firstError);
             return EvaluationOutcome.fail("Errors reported during fetch for job " + id + ": " + firstError,
                 StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // If fetched count is present but zero or negative -> treat as failure
         if (entity.getFetchedCount() != null && entity.getFetchedCount() <= 0) {
             logger.info("PetEnrichmentJob {} fetchedCount={}", id, entity.getFetchedCount());
             return EvaluationOutcome.fail("No items fetched for job " + id, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Missing petSource is a validation issue (job likely invalid)
         if (entity.getPetSource() == null || entity.getPetSource().isBlank()) {
             logger.warn("PetEnrichmentJob {} has missing petSource", id);
             return EvaluationOutcome.fail("petSource is required for PetEnrichmentJob " + id, StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // If none of the failure conditions met -> success
         return EvaluationOutcome.success();
    }
}