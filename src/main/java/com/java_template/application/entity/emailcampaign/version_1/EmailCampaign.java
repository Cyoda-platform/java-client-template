package com.java_template.application.entity.emailcampaign.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * ABOUTME: EmailCampaign entity represents a weekly email campaign that sends cat facts to subscribers.
 * Tracks campaign metadata, delivery status, and engagement metrics.
 */
@Data
public class EmailCampaign implements CyodaEntity {
    public static final String ENTITY_NAME = EmailCampaign.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String campaignId;

    // Required core business fields
    private Integer weekNumber;
    private String factId;
    private LocalDateTime scheduledDate;

    // Optional fields for additional business data
    private LocalDateTime sentDate;
    private Integer recipientCount;
    private Integer openCount;
    private Integer clickCount;
    private String subject;
    private String emailBody;

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
        return campaignId != null && !campaignId.trim().isEmpty() &&
               weekNumber != null &&
               factId != null && !factId.trim().isEmpty();
    }
}

