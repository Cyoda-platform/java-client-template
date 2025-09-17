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

import java.util.regex.Pattern;

/**
 * HNItemIndexingProcessor - Indexes HN items for search capabilities
 * 
 * Creates search index entries and manages parent-child relationships for search functionality.
 */
@Component
public class HNItemIndexingProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(HNItemIndexingProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    
    // Pattern to strip HTML tags
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");

    public HNItemIndexingProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing HNItem indexing for request: {}", request.getId());

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

        logger.debug("Indexing HNItem: {}", entity.getId());

        // Create search index entry (simulated - in real implementation would use search service)
        SearchDocument searchDocument = createSearchDocument(entity);
        logger.debug("Created search document for item: {}", entity.getId());

        // Update parent-child relationships in index (simulated)
        if (entity.getParent() != null) {
            logger.debug("Updating parent-child relationship: parent={}, child={}", entity.getParent(), entity.getId());
        }

        if (entity.getKids() != null && !entity.getKids().isEmpty()) {
            for (Long childId : entity.getKids()) {
                logger.debug("Updating parent-child relationship: parent={}, child={}", entity.getId(), childId);
            }
        }

        // Set indexing timestamp
        entity.setIndexedAt(System.currentTimeMillis());

        logger.info("HNItem {} indexed successfully", entity.getId());
        return entityWithMetadata;
    }

    private SearchDocument createSearchDocument(HNItem entity) {
        SearchDocument document = new SearchDocument();
        document.setId(entity.getId());
        document.setType(entity.getType());
        document.setTitle(entity.getTitle());
        document.setText(stripHtml(entity.getText()));
        document.setAuthor(entity.getBy());
        document.setScore(entity.getScore());
        document.setTime(entity.getTime());
        document.setUrl(entity.getUrl());
        return document;
    }

    private String stripHtml(String html) {
        if (html == null) {
            return null;
        }
        return HTML_TAG_PATTERN.matcher(html).replaceAll("");
    }

    /**
     * Internal class representing a search document
     */
    private static class SearchDocument {
        private Long id;
        private String type;
        private String title;
        private String text;
        private String author;
        private Integer score;
        private Long time;
        private String url;

        // Getters and setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        public String getAuthor() { return author; }
        public void setAuthor(String author) { this.author = author; }
        public Integer getScore() { return score; }
        public void setScore(Integer score) { this.score = score; }
        public Long getTime() { return time; }
        public void setTime(Long time) { this.time = time; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
    }
}
