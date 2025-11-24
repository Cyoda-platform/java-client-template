package com.java_template.application.entity.interaction.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * ABOUTME: Interaction entity tracks user interactions with email campaigns.
 * Records opens, clicks, and other engagement metrics for reporting purposes.
 */
@Data
public class Interaction implements CyodaEntity {
    public static final String ENTITY_NAME = Interaction.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String interactionId;

    // Required core business fields
    private String subscriberId;
    private String campaignId;
    private String interactionType;
    private LocalDateTime timestamp;

    // Optional fields for additional business data
    private String ipAddress;
    private String userAgent;
    private String linkClicked;

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
        return interactionId != null && !interactionId.trim().isEmpty() &&
               subscriberId != null && !subscriberId.trim().isEmpty() &&
               campaignId != null && !campaignId.trim().isEmpty() &&
               interactionType != null && !interactionType.trim().isEmpty();
    }
}

