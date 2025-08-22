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

import java.util.List;

@Component
public class ImportThresholdCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    /**
     * Maximum allowed imports for a single ingestion job. Jobs exceeding this are considered violations.
     * Chosen as a conservative operational cap for protecting downstream systems.
     */
    private static final long MAX_IMPORT_THRESHOLD = 5000L;

    /**
     * Maximum allowed number of per-job errors before flagging as a data quality issue.
     */
    private static final int MAX_ERRORS_THRESHOLD = 100;

    public ImportThresholdCriterion(SerializerFactory serializerFactory) {
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

         if (entity == null) {
             logger.warn("ImportThresholdCriterion invoked with null entity in context");
             return EvaluationOutcome.fail("Entity payload is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate importedCount exists and is non-negative
         Long importedCount = null;
         try {
             // handle possible getter variations: assume getImportedCount() exists and returns Number/Long/Integer
             Object countObj = entity.getImportedCount();
             if (countObj instanceof Number) {
                 importedCount = ((Number) countObj).longValue();
             }
         } catch (NoSuchMethodError | NoSuchMethodException | Throwable t) {
             // If getter does not exist or unexpected error, log and continue with null check (will fail below)
             logger.debug("Unable to read importedCount from PetIngestionJob: {}", t.toString());
         }

         if (importedCount == null) {
             return EvaluationOutcome.fail("importedCount is required for import threshold evaluation", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (importedCount < 0) {
             return EvaluationOutcome.fail("importedCount must be non-negative", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Business rule: do not allow extremely large single-job imports
         if (importedCount > MAX_IMPORT_THRESHOLD) {
             return EvaluationOutcome.fail(
                 String.format("importedCount (%d) exceeds allowed maximum of %d", importedCount, MAX_IMPORT_THRESHOLD),
                 StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Data quality check: too many errors on the job indicates source/data problems
         try {
             List<?> errors = entity.getErrors();
             if (errors != null && errors.size() > MAX_ERRORS_THRESHOLD) {
                 return EvaluationOutcome.fail(
                     String.format("job contains too many errors (%d)", errors.size()),
                     StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         } catch (NoSuchMethodError | NoSuchMethodException | Throwable t) {
             // If errors getter not present or unexpected, ignore and proceed — it's non-fatal for threshold evaluation
             logger.debug("Unable to read errors from PetIngestionJob: {}", t.toString());
         }

         // If we reach here, the job is within acceptable thresholds
         return EvaluationOutcome.success();
    }
}