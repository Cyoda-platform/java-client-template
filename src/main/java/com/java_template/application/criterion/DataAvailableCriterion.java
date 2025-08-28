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

@Component
public class DataAvailableCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public DataAvailableCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(PetIngestionJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // Must use exact criterion name (case-sensitive)
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<PetIngestionJob> context) {
         PetIngestionJob entity = context.entity();
         if (entity == null) {
             logger.warn("PetIngestionJob entity is null");
             return EvaluationOutcome.fail("Job entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String status = fetchStatus(entity);
         if (status == null || !status.equalsIgnoreCase("FETCHING")) {
             logger.debug("Job not in FETCHING state (status={})", status);
             return EvaluationOutcome.fail("Job is not in FETCHING state", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         Integer processedCount = fetchProcessedCount(entity);
         if (processedCount == null || processedCount <= 0) {
             logger.info("No data fetched for job (processedCount={})", processedCount);
             return EvaluationOutcome.fail("No records fetched by job", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         if (entity.getErrors() != null && !entity.getErrors().isEmpty()) {
             // Data is available, but there were errors. Log and proceed (treated as a warning by attachment strategy).
             logger.warn("Job fetched {} records but reported {} error(s): {}", processedCount, entity.getErrors().size(), entity.getErrors());
         } else {
             logger.debug("Job fetched {} records with no reported errors", processedCount);
         }

         logger.info("DataAvailableCriterion passed for job '{}': processedCount={}", entity.getJobName(), processedCount);
         return EvaluationOutcome.success();
    }

    private String fetchStatus(PetIngestionJob entity) {
        if (entity == null) return null;
        try {
            java.lang.reflect.Method m = entity.getClass().getMethod("getStatus");
            Object res = m.invoke(entity);
            return res == null ? null : res.toString();
        } catch (NoSuchMethodException e) {
            try {
                java.lang.reflect.Field f = entity.getClass().getDeclaredField("status");
                f.setAccessible(true);
                Object res = f.get(entity);
                return res == null ? null : res.toString();
            } catch (NoSuchFieldException | IllegalAccessException ex) {
                // fallback: search any no-arg method containing "status" or "state"
                for (java.lang.reflect.Method meth : entity.getClass().getMethods()) {
                    String n = meth.getName().toLowerCase();
                    if ((n.contains("status") || n.contains("state")) && meth.getParameterCount() == 0) {
                        try {
                            Object res = meth.invoke(entity);
                            return res == null ? null : res.toString();
                        } catch (Exception ign) {
                            // continue searching
                        }
                    }
                }
                logger.debug("Unable to access status on PetIngestionJob via reflection");
                return null;
            } catch (Exception ex) {
                logger.debug("Unexpected error accessing status via reflection", ex);
                return null;
            }
        } catch (Exception e) {
            logger.debug("Unexpected error accessing status via reflection", e);
            return null;
        }
    }

    private Integer fetchProcessedCount(PetIngestionJob entity) {
        if (entity == null) return null;
        try {
            java.lang.reflect.Method m = entity.getClass().getMethod("getProcessedCount");
            Object res = m.invoke(entity);
            if (res instanceof Number) return ((Number) res).intValue();
            if (res != null) return Integer.valueOf(res.toString());
            return null;
        } catch (NoSuchMethodException e) {
            try {
                java.lang.reflect.Field f = entity.getClass().getDeclaredField("processedCount");
                f.setAccessible(true);
                Object res = f.get(entity);
                if (res instanceof Number) return ((Number) res).intValue();
                if (res != null) return Integer.valueOf(res.toString());
                return null;
            } catch (NoSuchFieldException | IllegalAccessException ex) {
                // fallback: search any no-arg method or field with "processed" or "count" in the name
                for (java.lang.reflect.Method meth : entity.getClass().getMethods()) {
                    String n = meth.getName().toLowerCase();
                    if ((n.contains("processed") || n.contains("count")) && meth.getParameterCount() == 0) {
                        try {
                            Object res = meth.invoke(entity);
                            if (res instanceof Number) return ((Number) res).intValue();
                            if (res != null) return Integer.valueOf(res.toString());
                        } catch (Exception ign) {
                            // continue searching
                        }
                    }
                }
                for (java.lang.reflect.Field fld : entity.getClass().getDeclaredFields()) {
                    String n = fld.getName().toLowerCase();
                    if (n.contains("processed") || n.contains("count")) {
                        try {
                            fld.setAccessible(true);
                            Object res = fld.get(entity);
                            if (res instanceof Number) return ((Number) res).intValue();
                            if (res != null) return Integer.valueOf(res.toString());
                        } catch (Exception ign) {
                            // continue searching
                        }
                    }
                }
                logger.debug("Unable to access processedCount on PetIngestionJob via reflection");
                return null;
            } catch (Exception ex) {
                logger.debug("Unexpected error accessing processedCount via reflection", ex);
                return null;
            }
        } catch (Exception e) {
            logger.debug("Unexpected error accessing processedCount via reflection", e);
            return null;
        }
    }
}