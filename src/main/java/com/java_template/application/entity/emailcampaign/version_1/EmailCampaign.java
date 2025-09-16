package com.java_template.application.entity.emailcampaign.version_1;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

import static com.java_template.common.config.Config.ENTITY_VERSION;

/**
 * Represents an email campaign that sends cat facts to subscribers.
 * Manages campaign lifecycle through workflow states.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmailCampaign implements CyodaEntity {

    public static final String ENTITY_NAME = EmailCampaign.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    /**
     * Unique identifier for the email campaign
     */
    private Long id;

    /**
     * Name of the campaign (e.g., "Weekly Cat Facts - Week 1")
     */
    private String campaignName;

    /**
     * Reference to the cat fact being sent
     */
    private Long catFactId;

    /**
     * When the campaign is scheduled to be sent
     */
    private LocalDateTime scheduledDate;

    /**
     * When the campaign was actually sent (null if not sent)
     */
    private LocalDateTime sentDate;

    /**
     * Number of subscribers at the time of sending
     */
    private Integer totalSubscribers;

    /**
     * Number of successful email deliveries
     */
    private Integer successfulDeliveries;

    /**
     * Number of failed email deliveries
     */
    private Integer failedDeliveries;

    /**
     * Number of emails opened (if tracking is enabled)
     */
    private Integer openCount;

    /**
     * Number of links clicked (if tracking is enabled)
     */
    private Integer clickCount;

    /**
     * Number of unsubscribes triggered by this campaign
     */
    private Integer unsubscribeCount;

    @Override
    @JsonIgnore
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    @JsonIgnore
    public boolean isValid() {
        // Campaign name is required
        if (campaignName == null || campaignName.trim().isEmpty()) {
            return false;
        }
        
        // Cat fact ID is required
        if (catFactId == null) {
            return false;
        }
        
        // Scheduled date is required
        if (scheduledDate == null) {
            return false;
        }
        
        // Initialize counters if null
        if (totalSubscribers == null) {
            totalSubscribers = 0;
        }
        if (successfulDeliveries == null) {
            successfulDeliveries = 0;
        }
        if (failedDeliveries == null) {
            failedDeliveries = 0;
        }
        if (openCount == null) {
            openCount = 0;
        }
        if (clickCount == null) {
            clickCount = 0;
        }
        if (unsubscribeCount == null) {
            unsubscribeCount = 0;
        }
        
        return true;
    }
}
