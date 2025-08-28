package com.java_template.application.criterion;

import com.java_template.application.entity.user.version_1.User;
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
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class PastBehaviorCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private static final long MIN_DAYS_SINCE_REGISTRATION_FOR_TRUST = 90L;
    private static final int MIN_PRIOR_ADOPTIONS_FOR_TRUST = 1;

    public PastBehaviorCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(User.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // Use exact name match as required
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<User> context) {
         User user = context.entity();

         if (user == null) {
             return EvaluationOutcome.fail("User entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Helper accessors: prefer public getters; fall back to reflection if getters are not available.
         try {
             // EMAIL checks
             String email = readStringProperty(user, "email");
             if (email == null || email.isBlank()) {
                 return EvaluationOutcome.fail("Email is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
             if (!email.contains("@")) {
                 return EvaluationOutcome.fail("Email appears malformed", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }

             // STATUS check
             String status = readStringProperty(user, "status");
             if (status == null || status.isBlank()) {
                 return EvaluationOutcome.fail("User status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
             if ("SUSPENDED".equalsIgnoreCase(status)) {
                 return EvaluationOutcome.fail("User is suspended", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }

             // Past adoptions check
             List<String> adopted = readListProperty(user, "adoptedPetIds");
             if (adopted == null || adopted.isEmpty()) {
                 return EvaluationOutcome.fail("No prior adoptions recorded", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
             if (adopted.size() < MIN_PRIOR_ADOPTIONS_FOR_TRUST) {
                 return EvaluationOutcome.fail("Insufficient prior adoptions", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }

             // registeredAt / historical window check
             String registeredAt = readStringProperty(user, "registeredAt");
             if (registeredAt == null || registeredAt.isBlank()) {
                 return EvaluationOutcome.fail("registeredAt is required for past behavior evaluation", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             try {
                 OffsetDateTime reg = OffsetDateTime.parse(registeredAt);
                 long days = ChronoUnit.DAYS.between(reg, OffsetDateTime.now());
                 if (days < MIN_DAYS_SINCE_REGISTRATION_FOR_TRUST) {
                     return EvaluationOutcome.fail("User registered less than " + MIN_DAYS_SINCE_REGISTRATION_FOR_TRUST + " days ago", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
                 }
             } catch (DateTimeParseException ex) {
                 logger.warn("Unable to parse registeredAt for user {}: {}", safeReadString(user, "userId"), ex.getMessage());
                 return EvaluationOutcome.fail("registeredAt timestamp is malformed", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }

             // All checks passed
             return EvaluationOutcome.success();

         } catch (Exception ex) {
             logger.error("Unexpected error evaluating PastBehaviorCriterion for user {}: {}", safeReadString(user, "userId"), ex.getMessage(), ex);
             return EvaluationOutcome.fail("Internal error during evaluation", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
    }

    // Attempt to invoke getter first (getX or isX), then fall back to direct field access via reflection.
    private String readStringProperty(User user, String propName) {
        String v = safeInvokeGetter(user, propName);
        if (v != null) return v;
        Object fieldVal = safeReadField(user, propName);
        return fieldVal != null ? String.valueOf(fieldVal) : null;
    }

    @SuppressWarnings("unchecked")
    private List<String> readListProperty(User user, String propName) {
        // Try getter
        try {
            Method m = findMethod(user.getClass(), "get" + capitalize(propName));
            if (m != null) {
                Object res = m.invoke(user);
                if (res instanceof List) return (List<String>) res;
            }
            // fallback to field access
            Field f = findField(user.getClass(), propName);
            if (f != null) {
                f.setAccessible(true);
                Object res = f.get(user);
                if (res instanceof List) return (List<String>) res;
            }
        } catch (Exception e) {
            logger.debug("Failed to read list property '{}' via reflection: {}", propName, e.getMessage());
        }
        return null;
    }

    private String safeInvokeGetter(User user, String propName) {
        try {
            String getterName = "get" + capitalize(propName);
            Method m = findMethod(user.getClass(), getterName);
            if (m != null) {
                Object res = m.invoke(user);
                return res != null ? String.valueOf(res) : null;
            }
            // boolean-style getter
            Method m2 = findMethod(user.getClass(), "is" + capitalize(propName));
            if (m2 != null) {
                Object res = m2.invoke(user);
                return res != null ? String.valueOf(res) : null;
            }
        } catch (Exception e) {
            // ignore - handled by fallback
            logger.debug("safeInvokeGetter failed for property {}: {}", propName, e.getMessage());
        }
        return null;
    }

    private Object safeReadField(User user, String propName) {
        try {
            Field f = findField(user.getClass(), propName);
            if (f != null) {
                f.setAccessible(true);
                return f.get(user);
            }
        } catch (Exception e) {
            logger.debug("safeReadField failed for {}: {}", propName, e.getMessage());
        }
        return null;
    }

    private String safeReadString(User user, String propName) {
        try {
            return readStringProperty(user, propName);
        } catch (Exception e) {
            return null;
        }
    }

    private Method findMethod(Class<?> cls, String name) {
        try {
            for (Method m : cls.getMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == 0) return m;
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    private Field findField(Class<?> cls, String name) {
        Class<?> current = cls;
        while (current != null && !Object.class.equals(current)) {
            try {
                Field f = current.getDeclaredField(name);
                if (f != null) return f;
            } catch (NoSuchFieldException e) {
                // continue up the hierarchy
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}