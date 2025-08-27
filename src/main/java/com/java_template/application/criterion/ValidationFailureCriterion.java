package com.java_template.application.criterion;

import com.java_template.application.entity.laureate.version_1.Laureate;
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

import java.util.ArrayList;
import java.util.List;

@Component
public class ValidationFailureCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ValidationFailureCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Laureate.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        if (modelSpec == null || modelSpec.operationName() == null) return false;
        // Must match exact criterion name
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Laureate> context) {
         Laureate entity = context.entity();

         List<String> errors = new ArrayList<>();

         // Required fields
         if (entity.getLaureateId() == null || entity.getLaureateId().isBlank()) {
             errors.add("laureateId is required");
         }
         if (entity.getAwardYear() == null || entity.getAwardYear().isBlank()) {
             errors.add("awardYear is required");
         }
         if (entity.getCategory() == null || entity.getCategory().isBlank()) {
             errors.add("category is required");
         }
         if (entity.getProcessingStatus() == null || entity.getProcessingStatus().isBlank()) {
             errors.add("processingStatus is required");
         }

         // Provenance checks
         Laureate.Provenance prov = entity.getProvenance();
         if (prov == null) {
             errors.add("provenance is required");
         } else {
             if (prov.getIngestionJobId() == null || prov.getIngestionJobId().isBlank()) {
                 errors.add("provenance.ingestionJobId is required");
             }
             if (prov.getSourceRecordId() == null || prov.getSourceRecordId().isBlank()) {
                 errors.add("provenance.sourceRecordId is required");
             }
             if (prov.getSourceTimestamp() == null || prov.getSourceTimestamp().isBlank()) {
                 errors.add("provenance.sourceTimestamp is required");
             }
         }

         // Data quality: ageAtAward if present must be non-negative
         if (entity.getAgeAtAward() != null && entity.getAgeAtAward() < 0) {
             errors.add("ageAtAward must be non-negative");
         }

         // If validation errors found, populate entity.validationErrors and fail
         if (!errors.isEmpty()) {
             try {
                 entity.getValidationErrors().addAll(errors);
             } catch (Exception e) {
                 logger.warn("Failed to append validation errors to entity.validationErrors", e);
             }
             String message = String.join("; ", errors);
             return EvaluationOutcome.fail(message, StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // No validation failures
         return EvaluationOutcome.success();
    }
}