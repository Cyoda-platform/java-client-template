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
public class DeduplicationIsDuplicateCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public DeduplicationIsDuplicateCriterion(SerializerFactory serializerFactory) {
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
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Laureate> context) {
         Laureate entity = context.entity();
         // Basic required field checks (defensive)
         if (entity.getId() == null) {
            return EvaluationOutcome.fail("Laureate source id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getFirstname() == null || entity.getFirstname().isBlank()) {
             return EvaluationOutcome.fail("Laureate firstname is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getSurname() == null || entity.getSurname().isBlank()) {
             return EvaluationOutcome.fail("Laureate surname is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getYear() == null || entity.getYear().isBlank()) {
             return EvaluationOutcome.fail("Laureate award year is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Heuristic duplicate detection:
         // 1) If createdAt is already present the record appears to have been persisted before -> treat as duplicate.
         if (entity.getCreatedAt() != null && !entity.getCreatedAt().isBlank()) {
             logger.debug("Laureate id={} already has createdAt={}, marking as duplicate", entity.getId(), entity.getCreatedAt());
             return EvaluationOutcome.fail("Laureate already persisted (createdAt present) - considered duplicate", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // 2) Suspicious data that commonly indicates duplicate or bad import: identical firstname and surname
         String fn = entity.getFirstname().trim();
         String sn = entity.getSurname().trim();
         if (!fn.isEmpty() && fn.equalsIgnoreCase(sn)) {
             logger.debug("Laureate id={} firstname and surname identical ({}), marking as potential duplicate", entity.getId(), fn);
             return EvaluationOutcome.fail("Firstname and surname identical - likely duplicate or malformed record", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // 3) If minimal distinguishing information is missing (no born country/code and no affiliation), it is ambiguous and treated as duplicate-like record
         boolean hasBornInfo = (entity.getBornCountry() != null && !entity.getBornCountry().isBlank()) || (entity.getBornCountryCode() != null && !entity.getBornCountryCode().isBlank()) || (entity.getNormalizedCountryCode() != null && !entity.getNormalizedCountryCode().isBlank());
         boolean hasAffiliation = entity.getAffiliationName() != null && !entity.getAffiliationName().isBlank();
         if (!hasBornInfo && !hasAffiliation) {
             logger.debug("Laureate id={} lacks born country and affiliation, marking as potential duplicate/ambiguous record", entity.getId());
             return EvaluationOutcome.fail("Insufficient distinguishing data (no born country/code or affiliation) - treated as potential duplicate", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

        // If none of the duplicate heuristics matched, consider not a duplicate
        return EvaluationOutcome.success();
    }
}