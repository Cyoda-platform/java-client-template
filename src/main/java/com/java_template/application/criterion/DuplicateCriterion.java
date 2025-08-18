package com.java_template.application.criterion;

import com.java_template.application.entity.catfact.version_1.CatFact;
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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class DuplicateCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final EntityService entityService;

    public DuplicateCriterion(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(CatFact.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<CatFact> context) {
        CatFact catFact = context.entity();
        if (catFact == null) {
            return EvaluationOutcome.fail("CatFact is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (catFact.getText_hash() == null || catFact.getText_hash().isBlank()) {
            return EvaluationOutcome.fail("text_hash missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        try {
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.text_hash", "EQUALS", catFact.getText_hash())
            );
            CompletableFuture<ArrayNode> future = entityService.getItemsByCondition(
                CatFact.ENTITY_NAME,
                String.valueOf(CatFact.ENTITY_VERSION),
                condition,
                true
            );
            ArrayNode items = future.get();
            if (items != null && items.size() > 0) {
                // Need to ensure we don't count the entity itself as a duplicate. Use id or technicalId to exclude self.
                String currentId = null;
                try {
                    // Try to use domain id first
                    if (catFact.getId() != null && !catFact.getId().isBlank()) currentId = catFact.getId();
                    // Also consider technicalId from request context if available
                    String ctxEntityId = context.request() != null ? context.request().getEntityId() : null;
                    // Iterate results and see if any item is a different entity with same hash
                    for (int i = 0; i < items.size(); i++) {
                        ObjectNode node = (ObjectNode) items.get(i);
                        String foundId = null;
                        if (node.has("id") && !node.get("id").isNull()) foundId = node.get("id").asText();
                        String foundTechnical = null;
                        if (node.has("technicalId") && !node.get("technicalId").isNull()) foundTechnical = node.get("technicalId").asText();

                        // If found entity matches current (by id or technicalId) then ignore
                        if ((currentId != null && currentId.equals(foundId)) || (ctxEntityId != null && ctxEntityId.equals(foundTechnical))) {
                            continue;
                        }
                        // If any remaining item exists, this is a duplicate
                        return EvaluationOutcome.fail("Duplicate cat fact detected", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                    }
                } catch (Exception ex) {
                    logger.warn("Error while filtering duplicate results: {}", ex.getMessage());
                    return EvaluationOutcome.fail("Error checking duplicates", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                }
            }
        } catch (Exception ex) {
            logger.error("Error checking duplicate cat facts: {}", ex.getMessage(), ex);
            return EvaluationOutcome.fail("Error checking duplicates", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}
