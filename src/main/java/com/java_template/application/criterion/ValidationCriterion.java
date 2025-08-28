package com.java_template.application.criterion;

import com.java_template.application.entity.petimportjob.version_1.PetImportJob;
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

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Set;

@Component
public class ValidationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ValidationCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(PetImportJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<PetImportJob> context) {
         PetImportJob entity = context.entity();

         // requestId required
         if (entity.getRequestId() == null || entity.getRequestId().isBlank()) {
             return EvaluationOutcome.fail("requestId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // requestedAt required and must be ISO-8601 parseable
         if (entity.getRequestedAt() == null || entity.getRequestedAt().isBlank()) {
             return EvaluationOutcome.fail("requestedAt is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         try {
             Instant.parse(entity.getRequestedAt());
         } catch (DateTimeParseException ex) {
             logger.debug("Invalid requestedAt value: {}", entity.getRequestedAt());
             return EvaluationOutcome.fail("requestedAt must be a valid ISO-8601 timestamp", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // sourceUrl required and must look like an http/https URL
         if (entity.getSourceUrl() == null || entity.getSourceUrl().isBlank()) {
             return EvaluationOutcome.fail("sourceUrl is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         String src = entity.getSourceUrl().trim().toLowerCase();
         if (!(src.startsWith("http://") || src.startsWith("https://"))) {
             return EvaluationOutcome.fail("sourceUrl must be a valid http(s) URL", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // importedCount must be present and non-negative
         if (entity.getImportedCount() == null) {
             return EvaluationOutcome.fail("importedCount must be present", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (entity.getImportedCount() < 0) {
             return EvaluationOutcome.fail("importedCount must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // status required and must be one of allowed workflow states
         if (entity.getStatus() == null || entity.getStatus().isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         Set<String> allowed = Set.of("PENDING", "RUNNING", "COMPLETED", "FAILED");
         if (!allowed.contains(entity.getStatus().trim().toUpperCase())) {
             return EvaluationOutcome.fail("status must be one of PENDING, RUNNING, COMPLETED, FAILED", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

        return EvaluationOutcome.success();
    }
}