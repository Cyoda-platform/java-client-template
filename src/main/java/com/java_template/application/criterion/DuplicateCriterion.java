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
                // If there is any existing fact with same hash, consider duplicate
                return EvaluationOutcome.fail("Duplicate cat fact detected", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
            }
        } catch (Exception ex) {
            logger.error("Error checking duplicate cat facts: {}", ex.getMessage(), ex);
            return EvaluationOutcome.fail("Error checking duplicates", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}
