package com.java_template.application.criterion;

import com.java_template.application.entity.pet.version_1.Pet;
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
import java.time.temporal.ChronoUnit;

@Component
public class ArchiveCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ArchiveCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Pet.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Pet> context) {
         Pet entity = context.entity();

         if (entity == null) {
             return EvaluationOutcome.fail("Pet entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String status = entity.getStatus();
         if (status == null || status.isBlank()) {
             return EvaluationOutcome.fail("Pet status is required to evaluate archival", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Immediate archival if explicitly marked removed/deleted/archived
         if ("removed".equalsIgnoreCase(status)
                 || "deleted".equalsIgnoreCase(status)
                 || "archived".equalsIgnoreCase(status)) {
             return EvaluationOutcome.success();
         }

         // Automatic archival for old records: consider updatedAt first, fallback to sourceUpdatedAt
         String ts = entity.getUpdatedAt() != null && !entity.getUpdatedAt().isBlank()
                 ? entity.getUpdatedAt()
                 : entity.getSourceUpdatedAt();

         if (ts == null || ts.isBlank()) {
             // Not marked removed and no timestamp to make a time-based decision
             return EvaluationOutcome.fail("Pet not marked removed and no timestamps available to evaluate age-based archival", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         Instant instant;
         try {
             instant = Instant.parse(ts);
         } catch (DateTimeParseException ex) {
             logger.warn("Failed to parse timestamp for pet id {}: {}", entity.getId(), ts);
             return EvaluationOutcome.fail("Unable to parse timestamp for archival evaluation", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Age threshold: archive if not updated for 365 days or more
         Instant cutoff = Instant.now().minus(365, ChronoUnit.DAYS);
         if (instant.isBefore(cutoff)) {
             return EvaluationOutcome.success();
         }

         // Otherwise not eligible for archival
         return EvaluationOutcome.fail("Pet is not eligible for archival (not removed and not old enough)", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }
}