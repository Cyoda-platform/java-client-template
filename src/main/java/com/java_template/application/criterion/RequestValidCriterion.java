package com.java_template.application.criterion;

import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
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

import java.lang.reflect.Method;

@Component
public class RequestValidCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public RequestValidCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(AdoptionRequest.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // Must use exact criterion name (case-sensitive)
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<AdoptionRequest> context) {
         AdoptionRequest entity = context.entity();

         // Use reflection-based extraction for fields that may have different accessor names across versions
         String petId = getPropertyAsString(entity, "getPetId", "getPetID", "petId", "getPet");
         String requesterName = getPropertyAsString(entity, "getRequesterName", "requesterName", "getRequester");
         String contactEmail = getPropertyAsString(entity, "getContactEmail", "getEmail", "contactEmail");
         String contactPhone = getPropertyAsString(entity, "getContactPhone", "contactPhone", "getPhone");
         String motivation = getPropertyAsString(entity, "getMotivation", "motivation");
         String submittedAt = getPropertyAsString(entity, "getSubmittedAt", "submittedAt");
         String status = getPropertyAsString(entity, "getStatus", "status");

         // Basic required fields
         if (petId == null || petId.isBlank()) {
             return EvaluationOutcome.fail("petId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (requesterName == null || requesterName.isBlank()) {
             return EvaluationOutcome.fail("requesterName is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         // At least one contact method required
         boolean hasEmail = contactEmail != null && !contactEmail.isBlank();
         boolean hasPhone = contactPhone != null && !contactPhone.isBlank();
         if (!hasEmail && !hasPhone) {
             return EvaluationOutcome.fail("At least one contact method (contactEmail or contactPhone) is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (motivation == null || motivation.isBlank()) {
             return EvaluationOutcome.fail("motivation is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (submittedAt == null || submittedAt.isBlank()) {
             return EvaluationOutcome.fail("submittedAt is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (status == null || status.isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Data quality checks
         if (motivation != null && motivation.length() > 0 && motivation.length() < 10) {
             return EvaluationOutcome.fail("motivation is too short", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (hasEmail && !contactEmail.contains("@")) {
             return EvaluationOutcome.fail("contactEmail appears invalid", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // All validations passed
         return EvaluationOutcome.success();
    }

    /**
     * Attempts to extract a String representation for one of the candidate property accessor names.
     * It will try to invoke methods (no-arg) with the provided names on the target object. If a method
     * returns a String, that value is returned. If it returns an object that has a getId() method, that
     * will be invoked and its toString() returned. Otherwise the returned object's toString() is used.
     */
    private String getPropertyAsString(Object target, String... candidateAccessorNames) {
        if (target == null) return null;
        for (String accessor : candidateAccessorNames) {
            try {
                Method m = target.getClass().getMethod(accessor);
                Object val = m.invoke(target);
                if (val == null) continue;
                if (val instanceof String) return (String) val;
                // If returned object has getId(), prefer that
                try {
                    Method idm = val.getClass().getMethod("getId");
                    Object idv = idm.invoke(val);
                    if (idv != null) return idv.toString();
                } catch (NoSuchMethodException ignored) {
                }
                return val.toString();
            } catch (NoSuchMethodException ignored) {
                // try next candidate
            } catch (Exception e) {
                logger.debug("Error while attempting to read property '{}' via reflection: {}", accessor, e.getMessage());
            }
        }
        return null;
    }
}