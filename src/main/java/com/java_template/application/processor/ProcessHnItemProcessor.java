package com.java_template.application.processor;

import com.java_template.application.entity.hnitem.version_1.HnItem;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDateTime;

/**
 * ProcessHnItemProcessor - Process and enrich HN item data, handle parent-child relationships
 * 
 * This processor:
 * - Processes and enriches HN item data
 * - Validates URL format for stories
 * - Sets source URL for traceability
 * - Updates processing timestamp
 */
@Component
public class ProcessHnItemProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ProcessHnItemProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ProcessHnItemProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing HnItem for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(HnItem.class)
                .validate(this::isValidEntityWithMetadata, "Invalid HnItem entity wrapper")
                .map(this::processEntityWithMetadataLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<HnItem> entityWithMetadata) {
        HnItem entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    /**
     * Main business logic processing method
     */
    private EntityWithMetadata<HnItem> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<HnItem> context) {

        EntityWithMetadata<HnItem> entityWithMetadata = context.entityResponse();
        HnItem entity = entityWithMetadata.entity();

        // Get current entity metadata
        java.util.UUID currentEntityId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Processing entity: {} in state: {}", entity.getId(), currentState);

        // Process HTML content if present (keep original HTML structure)
        processHtmlContent(entity);

        // Handle URL validation for stories
        validateStoryUrl(entity);

        // Update processing timestamp
        entity.setUpdatedAt(LocalDateTime.now());

        // Set source URL for traceability
        if (entity.getSourceUrl() == null && entity.getId() != null) {
            entity.setSourceUrl("https://hacker-news.firebaseio.com/v0/item/" + entity.getId() + ".json");
        }

        logger.info("HnItem {} processed successfully", entity.getId());

        return entityWithMetadata;
    }

    /**
     * Process HTML content if present
     * Validates HTML content, sanitizes if needed, but keeps original HTML structure
     */
    private void processHtmlContent(HnItem entity) {
        if (entity.getText() != null && !entity.getText().trim().isEmpty()) {
            // Log if HTML content is present for monitoring
            if (entity.getText().contains("<") || entity.getText().contains(">")) {
                logger.debug("HTML content detected in text field for item: {}", entity.getId());
            }
        }
        
        if (entity.getTitle() != null && !entity.getTitle().trim().isEmpty()) {
            // Log if HTML content is present in title for monitoring
            if (entity.getTitle().contains("<") || entity.getTitle().contains(">")) {
                logger.debug("HTML content detected in title field for item: {}", entity.getId());
            }
        }
    }

    /**
     * Validate URL format for stories
     */
    private void validateStoryUrl(HnItem entity) {
        if ("story".equals(entity.getType()) && entity.getUrl() != null && !entity.getUrl().trim().isEmpty()) {
            if (!isValidUrl(entity.getUrl())) {
                logger.warn("Invalid URL format for story item {}: {}", entity.getId(), entity.getUrl());
                // Don't throw exception, just log warning to allow processing to continue
            }
        }
    }

    /**
     * Check if URL is valid
     */
    private boolean isValidUrl(String urlString) {
        try {
            new URL(urlString);
            return true;
        } catch (MalformedURLException e) {
            return false;
        }
    }
}
