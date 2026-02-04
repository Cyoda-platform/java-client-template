package com.java_template.application.entity.event.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Event Entity - Tracks interactions and events in the cat fact subscription system
 * 
 * This entity records:
 * - Event type (subscribe, unsubscribe, email_sent, email_opened, email_clicked)
 * - Timestamp of the event
 * - Metadata (subscriber email, campaign ID, etc.)
 */
@Data
public class Event implements CyodaEntity {
    public static final String ENTITY_NAME = "Event";
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier - event ID
    private String eventId;

    // Core business fields
    private String type; // subscribe, unsubscribe, email_sent, email_opened, email_clicked
    private LocalDateTime timestamp;
    private Map<String, String> metadata; // Flexible metadata storage

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid(EntityMetadata metadata) {
        // Validate required fields
        return eventId != null && !eventId.isBlank() && type != null && !type.isBlank();
    }
}

