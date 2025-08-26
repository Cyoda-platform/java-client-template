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

import java.util.ArrayList;
import java.util.List;

@Component
public class MissingRequiredFieldsCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public MissingRequiredFieldsCriterion(SerializerFactory serializerFactory) {
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
         if (entity == null) {
             logger.warn("Laureate entity is null in MissingRequiredFieldsCriterion");
             return EvaluationOutcome.fail("Entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         List<String> missing = new ArrayList<>();

         if (entity.getId() == null || entity.getId().isBlank()) missing.add("id");
         if (entity.getFullName() == null || entity.getFullName().isBlank()) missing.add("fullName");
         if (entity.getCategory() == null || entity.getCategory().isBlank()) missing.add("category");
         if (entity.getYear() == null || entity.getYear().isBlank()) missing.add("year");
         if (entity.getStatus() == null || entity.getStatus().isBlank()) missing.add("status");
         if (entity.getCreatedAt() == null || entity.getCreatedAt().isBlank()) missing.add("createdAt");

         if (!missing.isEmpty()) {
             String message = (missing.size() == 1 ? "Missing required field: " : "Missing required fields: ") + String.join(", ", missing);
             logger.debug("Laureate {} failed MissingRequiredFieldsCriterion: {}", entity.getId(), message);
             return EvaluationOutcome.fail(message, StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

        return EvaluationOutcome.success();
    }
}