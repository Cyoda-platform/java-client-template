package com.java_template.application.entity.emailcampaign.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * EmailCampaign Entity - Represents a weekly email campaign that sends a cat fact to all active subscribers
 * 
 * Entity States (managed by workflow):
 * - CREATED: Initial state when campaign is created
 * - SCHEDULED: Campaign is scheduled for execution
 * - EXECUTING: Campaign is currently being executed
 * - COMPLETED: Campaign execution completed successfully
 * - FAILED: Campaign execution failed
 * - CANCELLED: Campaign was cancelled before execution
 */
@Data
public class EmailCampaign implements CyodaEntity {
    public static final String ENTITY_NAME = EmailCampaign.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String id;
    
    // Required core business fields
    private String campaignName;
    
    // Optional fields for additional business data
    private String catFactId;
    private LocalDateTime scheduledDate;
    private LocalDateTime executedDate;
    private Integer totalSubscribers;
    private Integer successfulDeliveries;
    private Integer failedDeliveries;
    private String campaignType;

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
        if (campaignName == null || campaignName.trim().isEmpty()) {
            return false;
        }
        
        // Validate scheduled date is not in the past (if set)
        if (scheduledDate != null && scheduledDate.isBefore(LocalDateTime.now().minusMinutes(5))) {
            return false;
        }
        
        // Validate counters are non-negative
        if (totalSubscribers != null && totalSubscribers < 0) {
            return false;
        }
        if (successfulDeliveries != null && successfulDeliveries < 0) {
            return false;
        }
        if (failedDeliveries != null && failedDeliveries < 0) {
            return false;
        }
        
        // Validate that successful + failed deliveries don't exceed total subscribers
        if (totalSubscribers != null && successfulDeliveries != null && failedDeliveries != null) {
            if (successfulDeliveries + failedDeliveries > totalSubscribers) {
                return false;
            }
        }
        
        return true;
    }
}
