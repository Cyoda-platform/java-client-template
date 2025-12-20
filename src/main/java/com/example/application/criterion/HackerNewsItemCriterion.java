package com.example.application.criterion;

import com.example.application.entity.hacker_news_item.version_1.HackerNewsItem;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.CriterionCalculationRequest;
import org.cyoda.cloud.api.event.processing.CriterionCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * HackerNewsItemCriterion - Validates required fields for Hacker News items
 * 
 * This criterion checks that both 'id' and 'type' fields are present
 * in the incoming Hacker News item. If validation passes, the item
 * transitions to VALIDATED state. If it fails, it transitions to REJECTED.
 */
@Component
public class HackerNewsItemCriterion implements CyodaCriterion {

    private static final Logger logger = LoggerFactory.getLogger(HackerNewsItemCriterion.class);
    private final String className = this.getClass().getSimpleName();
    private final CriterionSerializer serializer;

    public HackerNewsItemCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriterionSerializer();
    }

    @Override
    public CriterionCalculationResponse evaluate(CriterionCalculationRequest request) {
        logger.info("Evaluating HackerNewsItem criterion for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(HackerNewsItem.class)
                .validate(this::isValidEntity, "Invalid entity wrapper")
                .map(this::evaluateValidation)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper
     */
    private boolean isValidEntity(EntityWithMetadata<HackerNewsItem> entityWithMetadata) {
        HackerNewsItem entity = entityWithMetadata.entity();
        return entity != null && entityWithMetadata.metadata().getId() != null;
    }

    /**
     * Evaluates whether the Hacker News item has required fields
     * Returns true if both id and type are present, false otherwise
     */
    private boolean evaluateValidation(
            CriterionSerializer.CriterionEntityResponseExecutionContext<HackerNewsItem> context) {
        
        EntityWithMetadata<HackerNewsItem> entityWithMetadata = context.entityResponse();
        HackerNewsItem item = entityWithMetadata.entity();

        boolean hasId = item.getId() != null;
        boolean hasType = item.getType() != null && !item.getType().isBlank();

        boolean isValid = hasId && hasType;
        
        logger.info("HackerNewsItem validation: id={}, type={}, valid={}",
                hasId, hasType, isValid);

        return isValid;
    }
}

