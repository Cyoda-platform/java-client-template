package com.java_template.application.processor;

import com.java_template.application.entity.hnitem.version_1.HNItem;
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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Pattern;

/**
 * HNItemEnrichmentProcessor - Enriches HN items with additional metadata and computed fields
 * 
 * Calculates derived fields, extracts URL metadata, and computes text statistics.
 */
@Component
public class HNItemEnrichmentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(HNItemEnrichmentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    
    // Pattern to strip HTML tags
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");

    public HNItemEnrichmentProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing HNItem enrichment for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(HNItem.class)
                .validate(this::isValidEntityWithMetadata, "Invalid HNItem entity wrapper")
                .map(this::processEntityWithMetadataLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<HNItem> entityWithMetadata) {
        HNItem entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    private EntityWithMetadata<HNItem> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<HNItem> context) {

        EntityWithMetadata<HNItem> entityWithMetadata = context.entityResponse();
        HNItem entity = entityWithMetadata.entity();

        logger.debug("Enriching HNItem: {}", entity.getId());

        // Calculate derived fields
        if (entity.getKids() != null) {
            entity.setDirectChildrenCount(entity.getKids().size());
        }

        // Enrich with URL metadata for stories
        if ("story".equals(entity.getType()) && entity.getUrl() != null && !entity.getUrl().trim().isEmpty()) {
            entity.setDomain(extractDomain(entity.getUrl()));
            entity.setUrlValid(validateUrl(entity.getUrl()));
        }

        // Calculate text statistics
        if (entity.getText() != null && !entity.getText().trim().isEmpty()) {
            String plainText = stripHtml(entity.getText());
            entity.setTextLength(plainText.length());
            entity.setWordCount(countWords(plainText));
        }

        // Set enrichment timestamp
        entity.setEnrichedAt(System.currentTimeMillis());

        logger.info("HNItem {} enriched successfully", entity.getId());
        return entityWithMetadata;
    }

    private String extractDomain(String url) {
        try {
            URL urlObj = new URL(url);
            return urlObj.getHost();
        } catch (MalformedURLException e) {
            logger.warn("Invalid URL format: {}", url);
            return null;
        }
    }

    private Boolean validateUrl(String url) {
        try {
            new URL(url);
            return true;
        } catch (MalformedURLException e) {
            return false;
        }
    }

    private String stripHtml(String html) {
        if (html == null) {
            return "";
        }
        return HTML_TAG_PATTERN.matcher(html).replaceAll("");
    }

    private Integer countWords(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0;
        }
        String[] words = text.trim().split("\\s+");
        return words.length;
    }
}
