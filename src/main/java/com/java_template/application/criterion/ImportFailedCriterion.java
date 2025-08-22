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
         PetIngestionJob job = context.entity();
         if (job == null) {
             logger.warn("PetIngestionJob entity is null in ImportFailedCriterion");
             return EvaluationOutcome.fail("Missing job entity", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate presence of an identifier
         try {
             if (job.getJobId() == null || job.getJobId().trim().isEmpty()) {
                 return EvaluationOutcome.fail("Job id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         } catch (Exception e) {
             // defensive: if getter not present or throws, treat as validation failure
             logger.debug("Exception while accessing jobId: {}", e.getMessage());
             return EvaluationOutcome.fail("Job id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Check status-related failure conditions
         String status = null;
         try {
             status = job.getStatus();
         } catch (Exception e) {
             logger.debug("Exception while accessing status: {}", e.getMessage());
         }

         if (status == null || status.trim().isEmpty()) {
             return EvaluationOutcome.fail("Job status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // If the ingestion job explicitly failed -> criterion fails (data quality/business impact)
         if ("failed".equalsIgnoreCase(status)) {
             return EvaluationOutcome.fail("Ingestion job reported status 'failed'", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // If completed but recorded errors -> surface as data quality failure
         if ("completed".equalsIgnoreCase(status)) {
             try {
                 java.util.List<String> errors = job.getErrors();
                 if (errors != null && !errors.isEmpty()) {
                     return EvaluationOutcome.fail(
                         String.format("Ingestion completed with %d error(s)", errors.size()),
                         StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
             } catch (Exception e) {
                 logger.debug("Exception while accessing errors: {}", e.getMessage());
                 // If errors property unavailable, continue without flagging
             }
         }

         // Additional sanity check: importedCount should be non-negative when present
         try {
             Integer importedCount = job.getImportedCount();
             if (importedCount != null && importedCount < 0) {
                 return EvaluationOutcome.fail("Imported count is negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         } catch (Exception e) {
             logger.debug("Exception while accessing importedCount: {}", e.getMessage());
         }

         // All checks passed
         return EvaluationOutcome.success();
    }
}