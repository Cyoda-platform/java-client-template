package com.java_template.application.criterion;

import com.java_template.application.entity.owner.version_1.Owner;
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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class ViolationDetectedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ViolationDetectedCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Owner.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Owner> context) {
         Owner entity = context.entity();

         if (entity == null) {
             logger.debug("Owner entity is null in ViolationDetectedCriterion");
             return EvaluationOutcome.fail("Owner entity missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // 1) If owner is VERIFIED but contact info is missing or invalid -> business rule failure (should be suspended)
         String verificationStatus = entity.getVerificationStatus();
         if ("VERIFIED".equalsIgnoreCase(verificationStatus)) {
             if (entity.getContactInfo() == null || !entity.getContactInfo().isValid()) {
                 return EvaluationOutcome.fail("Verified owner has invalid or missing contact information",
                     StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
         }

         // 2) If contact email exists but has an obviously invalid format -> data quality failure
         if (entity.getContactInfo() != null && entity.getContactInfo().getEmail() != null) {
             String email = entity.getContactInfo().getEmail();
             // simple sanity check for '@' presence
             if (!email.contains("@")) {
                 return EvaluationOutcome.fail("Owner contact email has invalid format",
                     StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // 3) If adoptedPetIds contains duplicate entries -> data quality failure
         List<String> adopted = entity.getAdoptedPetIds();
         if (adopted != null && adopted.size() > 1) {
             Set<String> seen = new HashSet<>();
             for (String pid : adopted) {
                 if (pid == null) continue;
                 if (!seen.add(pid)) {
                     return EvaluationOutcome.fail("Duplicate adopted pet id detected",
                         StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
             }
         }

         // No violation detected
         return EvaluationOutcome.success();
    }
}