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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

@Component
public class ValidPetDataCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ValidPetDataCriterion(SerializerFactory serializerFactory) {
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
        // Must match exact criterion/operation name
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Pet> context) {
         Pet entity = context.entity();
         if (entity == null) {
             logger.warn("Pet entity is null in ValidPetDataCriterion");
             return EvaluationOutcome.fail("Pet entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Use reflection to adapt to possible differences in getter names between versions
         String id = getStringProperty(entity, "getId", "getPetId", "id");
         String name = getStringProperty(entity, "getName", "getPetName", "name");
         String species = getStringProperty(entity, "getSpecies", "species");
         String status = getStringProperty(entity, "getStatus", "status");

         // Required core fields (based on Pet.isValid())
         if (id == null || id.isBlank()) {
             return EvaluationOutcome.fail("id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (name == null || name.isBlank()) {
             return EvaluationOutcome.fail("name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (species == null || species.isBlank()) {
             return EvaluationOutcome.fail("species is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (status == null || status.isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Photos: if present, entries must be non-blank and look like URLs
         List<String> photos = getProperty(entity, List.class, "getPhotos", "photos");
         if (photos != null) {
             for (String p : photos) {
                 if (p == null || p.isBlank()) {
                     return EvaluationOutcome.fail("photos contain blank entry", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
                 String lower = p.toLowerCase();
                 if (!(lower.startsWith("http://") || lower.startsWith("https://"))) {
                     return EvaluationOutcome.fail("photo URL invalid: " + p, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
             }
         }

         // Tags: if present, entries must be non-blank
         List<String> tags = getProperty(entity, List.class, "getTags", "tags");
         if (tags != null) {
             for (String t : tags) {
                 if (t == null || t.isBlank()) {
                     return EvaluationOutcome.fail("tags contain blank entry", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
             }
         }

         // importedAt: if present, must be ISO-8601 parseable
         String importedAt = getStringProperty(entity, "getImportedAt", "importedAt");
         if (importedAt != null && !importedAt.isBlank()) {
             try {
                 OffsetDateTime.parse(importedAt);
             } catch (DateTimeParseException ex) {
                 return EvaluationOutcome.fail("importedAt is not a valid ISO-8601 timestamp: " + importedAt,
                     StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // sex: if present, must be one of expected values
         String sex = getStringProperty(entity, "getSex", "sex");
         if (sex != null && !sex.isBlank()) {
             String s = sex.trim().toLowerCase();
             if (!(s.equals("m") || s.equals("f") || s.equals("unknown"))) {
                 return EvaluationOutcome.fail("sex must be 'M', 'F' or 'unknown' if provided", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // size: if present, validate allowed values
         String size = getStringProperty(entity, "getSize", "size");
         if (size != null && !size.isBlank()) {
             String sz = size.trim().toLowerCase();
             if (!(sz.equals("small") || sz.equals("medium") || sz.equals("large"))) {
                 return EvaluationOutcome.fail("size must be one of 'small', 'medium', 'large' if provided", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // Age: allow free-form but reject obviously invalid blanks handled above by isValid() requirements (not mandatory here)
         // Business rule example: species should be reasonable short value (avoid extremely long species strings)
         if (species != null && species.length() > 100) {
             return EvaluationOutcome.fail("species value is unusually long", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

        return EvaluationOutcome.success();
    }

    private String getStringProperty(Object obj, String... methodNames) {
        Object val = getProperty(obj, Object.class, methodNames);
        return val != null ? String.valueOf(val) : null;
    }

    @SuppressWarnings("unchecked")
    private <T> T getProperty(Object obj, Class<T> returnType, String... methodNames) {
        if (obj == null) return null;
        Class<?> cls = obj.getClass();
        for (String name : methodNames) {
            if (name == null) continue;
            String methodName = name;
            // If a plain field name provided, try getter convention
            if (!methodName.startsWith("get") && !methodName.startsWith("is")) {
                String cap = Character.toUpperCase(methodName.charAt(0)) + methodName.substring(1);
                methodName = "get" + cap;
            }
            try {
                Method m = cls.getMethod(methodName);
                Object res = m.invoke(obj);
                if (res == null) continue;
                if (returnType.isInstance(res)) {
                    return (T) res;
                } else {
                    // try string conversion if expected return type is String
                    if (returnType.equals(String.class)) {
                        return (T) String.valueOf(res);
                    }
                }
            } catch (NoSuchMethodException ignored) {
                // try next
            } catch (IllegalAccessException | InvocationTargetException e) {
                logger.debug("Failed to access property {} on {}: {}", methodName, cls.getName(), e.getMessage());
            }
        }
        return null;
    }
}