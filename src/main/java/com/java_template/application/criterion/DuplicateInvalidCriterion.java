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

@Component
public class DuplicateInvalidCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public DuplicateInvalidCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(Laureate.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        if (modelSpec == null || modelSpec.operationName() == null) return false;
        // Use exact criterion name (case-sensitive) as required
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Laureate> context) {
         Laureate entity = context.entity();
         if (entity == null) {
             logger.debug("DuplicateInvalidCriterion: received null entity");
             return EvaluationOutcome.fail("Entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required identity fields
         if (entity.getId() == null) {
             return EvaluationOutcome.fail("Missing source id", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         // Defensive: id should be positive (source ids are expected to be positive integers)
         if (entity.getId() != null && entity.getId() <= 0) {
             return EvaluationOutcome.fail("Invalid source id (must be positive)", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         if (entity.getFirstname() == null || entity.getFirstname().isBlank()) {
             return EvaluationOutcome.fail("Missing firstname", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getSurname() == null || entity.getSurname().isBlank()) {
             return EvaluationOutcome.fail("Missing surname", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getYear() == null || entity.getYear().isBlank()) {
             return EvaluationOutcome.fail("Missing award year", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         // ingestJobId is required to trace the origin of the record
         if (entity.getIngestJobId() == null || entity.getIngestJobId().isBlank()) {
             return EvaluationOutcome.fail("Missing ingestJobId (origin job reference)", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Data-quality check: computedAge must not be negative when present
         if (entity.getComputedAge() != null && entity.getComputedAge() < 0) {
             return EvaluationOutcome.fail("Computed age is negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Heuristic to detect likely duplicates with insufficient distinguishing attributes.
         // If most descriptive optional fields are missing/blank, treat as potential duplicate-invalid.
         int distinguishingCount = 0;
         if (entity.getBorn() != null && !entity.getBorn().isBlank()) distinguishingCount++;
         if (entity.getBorncity() != null && !entity.getBorncity().isBlank()) distinguishingCount++;
         if (entity.getBorncountry() != null && !entity.getBorncountry().isBlank()) distinguishingCount++;
         if (entity.getBorncountrycode() != null && !entity.getBorncountrycode().isBlank()) distinguishingCount++;
         if (entity.getMotivation() != null && !entity.getMotivation().isBlank()) distinguishingCount++;
         if (entity.getAffiliationName() != null && !entity.getAffiliationName().isBlank()) distinguishingCount++;
         if (entity.getAffiliationCity() != null && !entity.getAffiliationCity().isBlank()) distinguishingCount++;
         if (entity.getAffiliationCountry() != null && !entity.getAffiliationCountry().isBlank()) distinguishingCount++;

         // If fewer than two distinguishing attributes are present, mark as potential duplicate-invalid.
         if (distinguishingCount < 2) {
             logger.debug(
                 "DuplicateInvalidCriterion: laureate id={} has only {} distinguishing attributes (threshold=2)",
                 entity.getId(), distinguishingCount
             );
             return EvaluationOutcome.fail(
                 "Insufficient distinguishing attributes; record may be a duplicate or incomplete",
                 StandardEvalReasonCategories.DATA_QUALITY_FAILURE
             );
         }

         // If we reach here, entity passes duplicate-invalid heuristic
         return EvaluationOutcome.success();
    }
}