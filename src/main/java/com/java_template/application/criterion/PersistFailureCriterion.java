package com.java_template.application.criterion;

import com.java_template.application.entity.petingestionjob.version_1.PetIngestionJob;
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
import java.util.List;

@Component
public class PersistFailureCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public PersistFailureCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(PetIngestionJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<PetIngestionJob> context) {
         PetIngestionJob entity = context.entity();

         // Use reflection for access to methods that might not be present at compile time in some versions.
         String status = invokeStringGetter(entity, "getStatus");
         List<?> errors = invokeListGetter(entity, "getErrors");

         // Basic validation: status must be present
         if (status == null || status.isBlank()) {
            return EvaluationOutcome.fail("Job status is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         status = status.trim();

         // If job is explicitly marked FAILED, ensure errors are present and report failure
         if ("FAILED".equalsIgnoreCase(status)) {
             if (errors == null || errors.isEmpty()) {
                 return EvaluationOutcome.fail("Job marked FAILED but no error details provided", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
             int errs = errors.size();
             return EvaluationOutcome.fail("Persist stage failed with " + errs + " error(s)", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // If currently persisting but produced errors and no records persisted -> treat as persist failure
         if ("PERSISTING".equalsIgnoreCase(status)) {
             boolean hasErrors = errors != null && !errors.isEmpty();
             Integer processedCount = null;
             try {
                 // try to call getProcessedCount() if present
                 Method m = entity.getClass().getMethod("getProcessedCount");
                 Object rc = m.invoke(entity);
                 if (rc instanceof Integer) {
                     processedCount = (Integer) rc;
                 } else if (rc instanceof Number) {
                     processedCount = ((Number) rc).intValue();
                 }
             } catch (NoSuchMethodException nsme) {
                 // method not present; leave processedCount as null
             } catch (Exception e) {
                 logger.debug("Error invoking getProcessedCount on {}: {}", entity.getClass(), e.getMessage());
             }
             boolean noProcessed = processedCount == null || processedCount == 0;
             if (hasErrors && noProcessed) {
                 return EvaluationOutcome.fail("Persisting produced errors and no records were persisted", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // If completed, ensure completedAt and processedCount look sane
         if ("COMPLETED".equalsIgnoreCase(status)) {
             String completedAt = invokeStringGetter(entity, "getCompletedAt");
             if (completedAt == null || completedAt.isBlank()) {
                 return EvaluationOutcome.fail("Job marked COMPLETED but completedAt is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
             try {
                 Method m = entity.getClass().getMethod("getProcessedCount");
                 Object rc = m.invoke(entity);
                 Integer processedCount = null;
                 if (rc instanceof Integer) {
                     processedCount = (Integer) rc;
                 } else if (rc instanceof Number) {
                     processedCount = ((Number) rc).intValue();
                 }
                 if (processedCount == null || processedCount < 0) {
                     return EvaluationOutcome.fail("Job marked COMPLETED but processedCount is invalid", StandardEvalReasonCategories.VALIDATION_FAILURE);
                 }
             } catch (NoSuchMethodException nsme) {
                 // If processedCount getter absent, cannot validate; assume OK
             } catch (Exception e) {
                 logger.debug("Error invoking getProcessedCount on {}: {}", entity.getClass(), e.getMessage());
             }
         }

         // No persist-failure conditions detected
         return EvaluationOutcome.success();
    }

    @SuppressWarnings("unchecked")
    private List<?> invokeListGetter(Object obj, String methodName) {
        if (obj == null) return null;
        try {
            Method m = obj.getClass().getMethod(methodName);
            Object res = m.invoke(obj);
            if (res instanceof List) {
                return (List<?>) res;
            }
        } catch (NoSuchMethodException nsme) {
            logger.debug("Method {} not found on {}", methodName, obj.getClass().getName());
        } catch (Exception e) {
            logger.debug("Error invoking {} on {}: {}", methodName, obj.getClass().getName(), e.getMessage());
        }
        return null;
    }

    private String invokeStringGetter(Object obj, String methodName) {
        if (obj == null) return null;
        try {
            Method m = obj.getClass().getMethod(methodName);
            Object res = m.invoke(obj);
            return res == null ? null : res.toString();
        } catch (NoSuchMethodException nsme) {
            logger.debug("Method {} not found on {}", methodName, obj.getClass().getName());
        } catch (Exception e) {
            logger.debug("Error invoking {} on {}: {}", methodName, obj.getClass().getName(), e.getMessage());
        }
        return null;
    }
}