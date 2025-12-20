package com.example.application.processor;

import com.example.application.entity.hacker_news_item.version_1.HackerNewsItem;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * HackerNewsItemProcessor - Enriches Hacker News items with import timestamp
 *
 * This processor adds the importTimestamp field to the Hacker News item
 * during the enrichAndStore transition. The timestamp is set to the
 * current server time when the item is processed.
 */
@Component
public class HackerNewsItemProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(HackerNewsItemProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public HackerNewsItemProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing HackerNewsItem for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(HackerNewsItem.class)
                .validate(this::isValidEntityWithMetadata, "Invalid entity wrapper")
                .map(this::enrichItem)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<HackerNewsItem> entityWithMetadata) {
        HackerNewsItem entity = entityWithMetadata.entity();
        return entity != null && entity.isValid(entityWithMetadata.metadata())
                && entityWithMetadata.metadata().getId() != null;
    }

    /**
     * Enriches the Hacker News item with importTimestamp
     */
    private EntityWithMetadata<HackerNewsItem> enrichItem(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<HackerNewsItem> context) {

        EntityWithMetadata<HackerNewsItem> entityWithMetadata = context.entityResponse();
        HackerNewsItem item = entityWithMetadata.entity();

        // Add import timestamp (current server time)
        item.setImportTimestamp(Instant.now());

        logger.info("HackerNewsItem {} enriched with importTimestamp: {}",
                item.getId(), item.getImportTimestamp());

        return entityWithMetadata;
    }
}

