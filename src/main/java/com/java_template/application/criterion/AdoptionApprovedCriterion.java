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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Objects;

@Component
public class AdoptionApprovedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public AdoptionApprovedCriterion(SerializerFactory serializerFactory) {
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
        // must use exact criterion name
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<AdoptionRequest> context) {
         AdoptionRequest entity = context.entity();
         if (entity == null) {
             return EvaluationOutcome.fail("AdoptionRequest entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Read properties using available access (try getter first, fallback to field access).
         String status = getStringProperty(entity, "status");
         // If not approved, criterion is not applicable -> success (no failure)
         if (status == null || !status.equalsIgnoreCase("approved")) {
             return EvaluationOutcome.success();
         }

         // For approved requests enforce presence of reviewerId and decisionAt and submittedAt
         String reviewerId = getStringProperty(entity, "reviewerId");
         if (reviewerId == null || reviewerId.isBlank()) {
             return EvaluationOutcome.fail("Approved adoption request must have a reviewerId", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         String decisionAt = getStringProperty(entity, "decisionAt");
         if (decisionAt == null || decisionAt.isBlank()) {
             return EvaluationOutcome.fail("Approved adoption request must have decisionAt timestamp", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String submittedAt = getStringProperty(entity, "submittedAt");
         if (submittedAt == null || submittedAt.isBlank()) {
             return EvaluationOutcome.fail("Adoption request submittedAt timestamp is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate timestamp formats and ordering (decision must not be before submission)
         try {
             Instant submitted = Instant.parse(submittedAt);
             Instant decision = Instant.parse(decisionAt);
             if (decision.isBefore(submitted)) {
                 return EvaluationOutcome.fail("decisionAt is before submittedAt", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
         } catch (DateTimeException ex) {
             String reqId = getStringProperty(entity, "requestId");
             logger.debug("Timestamp parse error for AdoptionRequest {}: {}", reqId != null ? reqId : "unknown", ex.getMessage());
             return EvaluationOutcome.fail("Invalid ISO-8601 timestamp in submittedAt or decisionAt", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

        return EvaluationOutcome.success();
    }

    /**
     * Try to obtain a String property value from the entity.
     * First attempts to call a conventional getter (getX or isX), then falls back to direct field access.
     */
    private String getStringProperty(AdoptionRequest entity, String propertyName) {
        if (entity == null || propertyName == null) return null;
        // Try getter methods
        String capitalized = capitalize(propertyName);
        String[] methodNames = new String[] { "get" + capitalized, "is" + capitalized, propertyName };
        for (String mName : methodNames) {
            try {
                Method m = entity.getClass().getMethod(mName);
                Object v = m.invoke(entity);
                if (v != null) return v.toString();
            } catch (NoSuchMethodException ignored) {
                // try next
            } catch (Exception ex) {
                logger.debug("Error invoking method '{}' on {}: {}", mName, entity.getClass().getSimpleName(), ex.getMessage());
                // fallthrough to try field access
            }
        }

        // Fallback to field access
        try {
            Field f = findField(entity.getClass(), propertyName);
            if (f != null) {
                f.setAccessible(true);
                Object v = f.get(entity);
                if (v != null) return v.toString();
            }
        } catch (Exception ex) {
            logger.debug("Reflection access error for field '{}' on {}: {}", propertyName, entity.getClass().getSimpleName(), ex.getMessage());
        }
        return null;
    }

    private Field findField(Class<?> cls, String name) {
        Class<?> current = cls;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private String capitalize(String s) {
        if (s == null || s.isBlank()) return s;
        if (s.length() == 1) return s.toUpperCase();
        return s.substring(0,1).toUpperCase() + s.substring(1);
    }
}