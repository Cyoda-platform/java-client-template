package com.java_template.application.criterion;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.game.version_1.Game;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class DeduplicateGameCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final EntityService entityService;
    private final String className = this.getClass().getSimpleName();

    public DeduplicateGameCriterion(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(Game.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Game> context) {
        Game game = context.entity();
        if (game == null) {
            return EvaluationOutcome.fail("Game is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        // Deduplicate based on gameId + date
        String gameId = game.getGameId();
        String date = game.getDate();
        if (gameId == null || gameId.isBlank() || date == null || date.isBlank()) {
            return EvaluationOutcome.fail("Missing gameId or date", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        try {
            Object condition = SearchConditionRequest.group("AND",
                Condition.of("$.gameId", "EQUALS", gameId),
                Condition.of("$.date", "EQUALS", date)
            );
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(Game.ENTITY_NAME, String.valueOf(Game.ENTITY_VERSION), condition, true);
            ArrayNode items = itemsFuture.get();
            if (items != null && items.size() > 0) {
                logger.info("Duplicate game detected for {} on {}", gameId, date);
                return EvaluationOutcome.fail("Duplicate game", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
            }
        } catch (Exception e) {
            logger.warn("Failed to check duplicate for game {} on {}: {}", gameId, date, e.getMessage());
            return EvaluationOutcome.fail("Failed to check duplicates", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}
