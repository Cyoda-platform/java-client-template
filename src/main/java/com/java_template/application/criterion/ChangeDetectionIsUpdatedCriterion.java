package com.java_template.application.criterion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class ChangeDetectionIsUpdatedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final EntityService entityService;

    public ChangeDetectionIsUpdatedCriterion(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(Laureate.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Laureate> context) {
        Laureate incoming = context.entity();
        if (incoming == null) {
            return EvaluationOutcome.fail("Laureate is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // If no business id, cannot be update
        if (incoming.getId() == null || incoming.getId().isBlank()) {
            return EvaluationOutcome.fail("No business id provided", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        try {
            CompletableFuture<ArrayNode> fut = entityService.getItemsByCondition(Laureate.ENTITY_NAME, String.valueOf(Laureate.ENTITY_VERSION),
                SearchConditionRequest.group("AND", Condition.of("$.id", "EQUALS", incoming.getId())));
            ArrayNode arr = fut.get();
            if (arr == null || arr.isEmpty()) {
                return EvaluationOutcome.fail("No existing laureate with given id", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
            }
            ObjectNode existing = (ObjectNode) arr.get(0);
            // Compare material fields: fullName, year, category, affiliation, citation
            String existingFull = existing.has("fullName") ? existing.get("fullName").asText() : null;
            String existingYear = existing.has("year") ? existing.get("year").asText() : null;
            String existingCategory = existing.has("category") ? existing.get("category").asText() : null;
            String existingAff = existing.has("affiliation") ? existing.get("affiliation").asText() : null;
            String existingCit = existing.has("citation") ? existing.get("citation").asText() : null;

            if (!equalsNormalized(existingFull, incoming.getFullName())
                || !equalsNormalized(existingYear, incoming.getYear())
                || !equalsNormalized(existingCategory, incoming.getCategory())
                || !equalsNormalized(existingAff, incoming.getAffiliation())
                || !equalsNormalized(existingCit, incoming.getCitation())) {
                return EvaluationOutcome.success();
            }
            return EvaluationOutcome.fail("No material differences detected", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        } catch (Exception e) {
            logger.warn("Error during update detection for {}: {}", incoming.getId(), e.getMessage());
            // Fallback assume updated to be safe
            return EvaluationOutcome.success();
        }
    }

    private boolean equalsNormalized(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.trim().equalsIgnoreCase(b.trim());
    }
}
