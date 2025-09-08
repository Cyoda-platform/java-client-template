package com.java_template.application.entity.emailcampaign.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * EmailCampaign Entity - Represents a weekly email campaign that sends cat facts to subscribers
 * 
 * Entity State Management:
 * - PENDING: Delivery queued for sending
 * - SENT: Email sent to email service
 * - DELIVERED: Email delivered to recipient's inbox
 * - FAILED: Email delivery failed
 */
@Data
public class EmailCampaign implements CyodaEntity {
    public static final String ENTITY_NAME = EmailCampaign.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required unique identifier for the campaign
    private String campaignId;
    
    // Required campaign information
    private String campaignName;
    private String catFactId;
    
    // Required scheduling information
    private LocalDateTime scheduledDate;
    private LocalDateTime actualSentDate;
    
    // Required subscriber and delivery tracking
    private Integer totalSubscribers;
    private Integer successfulDeliveries;
    private Integer failedDeliveries;
    private Integer bounces;
    private Integer unsubscribes;
    
    // Email engagement tracking
    private Integer opens;
    private Integer clicks;
    
    // Required email content
    private String emailSubject;
    private String emailTemplate;

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
        if (campaignId == null || campaignId.trim().isEmpty()) {
            return false;
        }
        
        if (campaignName == null || campaignName.trim().isEmpty()) {
            return false;
        }
        
        if (catFactId == null || catFactId.trim().isEmpty()) {
            return false;
        }
        
        if (scheduledDate == null) {
            return false;
        }
        
        if (totalSubscribers == null || totalSubscribers < 0) {
            return false;
        }
        
        if (successfulDeliveries == null || successfulDeliveries < 0) {
            return false;
        }
        
        if (failedDeliveries == null || failedDeliveries < 0) {
            return false;
        }
        
        if (bounces == null || bounces < 0) {
            return false;
        }
        
        if (unsubscribes == null || unsubscribes < 0) {
            return false;
        }
        
        if (opens == null || opens < 0) {
            return false;
        }
        
        if (clicks == null || clicks < 0) {
            return false;
        }
        
        if (emailSubject == null || emailSubject.trim().isEmpty()) {
            return false;
        }
        
        if (emailTemplate == null || emailTemplate.trim().isEmpty()) {
            return false;
        }
        
        return true;
    }
}
