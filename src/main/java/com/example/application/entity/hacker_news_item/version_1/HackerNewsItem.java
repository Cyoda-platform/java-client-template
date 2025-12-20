package com.example.application.entity.hacker_news_item.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/**
 * HackerNewsItem Entity - Stores Hacker News items from Firebase API
 * 
 * This entity stores raw JSON payloads from the Firebase Hacker News API
 * with minimal structural modification. The rawJson field preserves the
 * original API response, while id and type are extracted for validation.
 * importTimestamp is added during enrichment workflow.
 */
@Data
public class HackerNewsItem implements CyodaEntity {
    public static final String ENTITY_NAME = "HackerNewsItem";
    public static final Integer ENTITY_VERSION = 1;

    // Business identifier - the Hacker News item ID
    private Long id;

    // Item type from Firebase API (story, comment, job, poll, pollopt)
    private String type;

    // Original JSON payload from Firebase Hacker News API
    private JsonNode rawJson;

    // Server-side enrichment: timestamp when item was imported
    private Instant importTimestamp;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid(EntityMetadata metadata) {
        // Validate required fields: id and type must be present
        return id != null && type != null && !type.isBlank();
    }
}

