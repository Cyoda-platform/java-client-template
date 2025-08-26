package com.java_template.application.criterion;

import com.java_template.application.entity.hnitem.version_1.HNItem;
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
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(HNItem.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // Must match exact criterion name
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<HNItem> context) {
         HNItem entity = context.entity();
         if (entity == null) {
             logger.warn("HNItem entity is null in PersistFailureCriterion");
             return EvaluationOutcome.success();
         }

         // Use reflection to safely obtain properties so compilation does not depend on generated accessor presence.
         String status = null;
         Long id = null;

         // Try getter methods first (if present)
         try {
             Method getStatus = entity.getClass().getMethod("getStatus");
             Object s = getStatus.invoke(entity);
             status = s != null ? s.toString() : null;
         } catch (NoSuchMethodException ignored) {
             // fallback to field access
             try {
                 Field statusField = entity.getClass().getDeclaredField("status");
                 statusField.setAccessible(true);
                 Object s = statusField.get(entity);
                 status = s != null ? s.toString() : null;
             } catch (Exception ex) {
                 logger.debug("Unable to read status from HNItem via reflection: {}", ex.getMessage());
             }
         } catch (Exception ex) {
             logger.debug("Error invoking getStatus on HNItem: {}", ex.getMessage());
         }

         try {
             Method getId = entity.getClass().getMethod("getId");
             Object i = getId.invoke(entity);
             if (i instanceof Number) id = ((Number) i).longValue();
         } catch (NoSuchMethodException ignored) {
             // fallback to field access
             try {
                 Field idField = entity.getClass().getDeclaredField("id");
                 idField.setAccessible(true);
                 Object i = idField.get(entity);
                 if (i instanceof Number) id = ((Number) i).longValue();
             } catch (Exception ex) {
                 logger.debug("Unable to read id from HNItem via reflection: {}", ex.getMessage());
             }
         } catch (Exception ex) {
             logger.debug("Error invoking getId on HNItem: {}", ex.getMessage());
         }

         if (status != null && status.equalsIgnoreCase("FAILED")) {
             String msg = "Persistence step failed for HNItem";
             if (id != null) msg += " id=" + id;
             logger.info(msg);
             return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Not a persistence failure -> success for this criterion
         return EvaluationOutcome.success();
    }
}