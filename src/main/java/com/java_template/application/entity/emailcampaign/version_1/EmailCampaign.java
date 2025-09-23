package com.java_template.application.entity.emailcampaign.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * EmailCampaign Entity - Tracks email sending campaigns and statistics
 * 
 * This entity represents email campaigns that send cat facts to subscribers.
 * It tracks campaign metrics, delivery status, and performance statistics.
 */
@Data
public class EmailCampaign implements CyodaEntity {
    public static final String ENTITY_NAME = EmailCampaign.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String campaignId;
    
    // Required core business fields
    private String catFactId; // Reference to CatFact business ID
    private LocalDateTime sentDate;
    private Integer recipientCount;
    private Integer successCount;
    private Integer failureCount;

    // Optional fields for additional business data
    private String campaignName;
    private String emailSubject;
    private String emailTemplate;
    private CampaignStatus status;
    private List<EmailDeliveryResult> deliveryResults;
    private CampaignMetrics metrics;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required fields
        return campaignId != null && !campaignId.trim().isEmpty() &&
               catFactId != null && !catFactId.trim().isEmpty() &&
               recipientCount != null && recipientCount >= 0 &&
               successCount != null && successCount >= 0 &&
               failureCount != null && failureCount >= 0;
    }

    /**
     * Enum for campaign status
     */
    public enum CampaignStatus {
        DRAFT, SCHEDULED, SENDING, COMPLETED, FAILED, CANCELLED
    }

    /**
     * Nested class for individual email delivery results
     */
    @Data
    public static class EmailDeliveryResult {
        private String subscriberId;
        private String email;
        private String deliveryStatus; // SENT, DELIVERED, BOUNCED, FAILED
        private String errorMessage;
        private LocalDateTime deliveryTime;
        private Boolean opened;
        private Boolean clicked;
    }

    /**
     * Nested class for campaign performance metrics
     */
    @Data
    public static class CampaignMetrics {
        private Double deliveryRate;
        private Double openRate;
        private Double clickRate;
        private Double bounceRate;
        private Integer totalOpens;
        private Integer totalClicks;
        private Integer totalBounces;
        private LocalDateTime lastUpdated;
    }
}
