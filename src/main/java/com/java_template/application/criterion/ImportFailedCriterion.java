package com.java_template.application.criterion;

import com.java_template.application.entity.pet_ingestion_job.version_1.PetIngestionJob;
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

import java.util.List;
import java.util.Objects;

@Component
public class ImportFailedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ImportFailedCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(PetIngestionJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<PetIngestionJob> context) {
         PetIngestionJob entity = context.entity();

         // Basic presence checks
         if (entity == null) {
             return EvaluationOutcome.fail("Entity payload is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String status = null;
         try {
             status = entity.getStatus();
         } catch (Exception e) {
             logger.debug("Unable to read status from PetIngestionJob", e);
         }

         if (status == null || status.trim().isEmpty()) {
             return EvaluationOutcome.fail("status is required for ingestion jobs", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // On failed jobs, ensure errors are captured
         if ("failed".equalsIgnoreCase(status.trim())) {
             List<String> errors = null;
             try {
                 errors = entity.getErrors();
             } catch (Exception e) {
                 logger.debug("Unable to read errors from PetIngestionJob", e);
             }

             if (errors == null || errors.isEmpty()) {
                 return EvaluationOutcome.fail("Ingestion job marked as 'failed' but no error details were recorded", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }

             // If there are errors recorded, surface as a business rule failure (job failed as expected)
             return EvaluationOutcome.fail("Ingestion job failed with recorded errors", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Completed jobs with errors or zero imports indicate potential data quality issues
         if ("completed".equalsIgnoreCase(status.trim())) {
             Integer importedCount = null;
             List<String> errors = null;
             try {
                 importedCount = entity.getImportedCount();
             } catch (Exception e) {
                 logger.debug("Unable to read importedCount from PetIngestionJob", e);
             }
             try {
                 errors = entity.getErrors();
             } catch (Exception e) {
                 logger.debug("Unable to read errors from PetIngestionJob", e);
             }

             if ((importedCount == null || importedCount == 0) && errors != null && !errors.isEmpty()) {
                 return EvaluationOutcome.fail("Job completed but importedCount is zero while errors were recorded", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // For running/pending or successful completed jobs with no anomalies, consider success
         return EvaluationOutcome.success();
    }
}