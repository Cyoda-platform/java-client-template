package com.java_template.application.criterion;

import com.java_template.application.entity.petingestionjob.version_1.PetIngestionJob;
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
public class DataAvailableCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public DataAvailableCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(PetIngestionJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // Must use exact criterion name (case-sensitive)
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<PetIngestionJob> context) {
         PetIngestionJob entity = context.entity();
         if (entity == null) {
             logger.warn("PetIngestionJob entity is null");
             return EvaluationOutcome.fail("Job entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String status = entity.getStatus();
         if (status == null || !status.equalsIgnoreCase("FETCHING")) {
             logger.debug("Job not in FETCHING state (status={})", status);
             return EvaluationOutcome.fail("Job is not in FETCHING state", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         Integer processedCount = entity.getProcessedCount();
         if (processedCount == null || processedCount <= 0) {
             logger.info("No data fetched for job (processedCount={})", processedCount);
             return EvaluationOutcome.fail("No records fetched by job", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         if (entity.getErrors() != null && !entity.getErrors().isEmpty()) {
             // Data is available, but there were errors. Log and proceed (treated as a warning by attachment strategy).
             logger.warn("Job fetched {} records but reported {} error(s): {}", processedCount, entity.getErrors().size(), entity.getErrors());
         } else {
             logger.debug("Job fetched {} records with no reported errors", processedCount);
         }

         logger.info("DataAvailableCriterion passed for job '{}': processedCount={}", entity.getJobName(), processedCount);
         return EvaluationOutcome.success();
    }
}