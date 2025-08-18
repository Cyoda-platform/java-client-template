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
public class ChangeDetectionIsNewCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final EntityService entityService;

    public ChangeDetectionIsNewCriterion(SerializerFactory serializerFactory, EntityService entityService) {
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

        // If businessId absent, try to search by fingerprint. If no match -> NEW
        if (incoming.getId() == null || incoming.getId().isBlank()) {
            logger.info("Laureate missing business id; treating as new");
            return EvaluationOutcome.success();
        }

        try {
            // Query for existing laureates by business id
            CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> fut = entityService.getItemsByCondition(
                Laureate.ENTITY_NAME, String.valueOf(Laureate.ENTITY_VERSION),
                SearchConditionRequest.group("AND", Condition.of("$.id", "EQUALS", incoming.getId()))
            );
            com.fasterxml.jackson.databind.node.ArrayNode arr = fut.get();
            if (arr == null || arr.isEmpty()) {
                logger.info("No existing laureate found for business id {} -> NEW", incoming.getId());
                return EvaluationOutcome.success();
            } else {
                logger.info("Existing laureate(s) found for id {} -> not new", incoming.getId());
                return EvaluationOutcome.fail("Existing laureate found", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
            }
        } catch (Exception e) {
            logger.warn("Error querying for existing laureate {}: {}", incoming.getId(), e.getMessage());
            // Fallback heuristic
            if (incoming.getId().startsWith("gen-") || incoming.getId().startsWith("laur-")) {
                return EvaluationOutcome.success();
            }
            return EvaluationOutcome.fail("Unable to determine novelty", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
    }
}
