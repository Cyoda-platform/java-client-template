package com.java_template.application.entity.emaildelivery.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * EmailDelivery Entity - Represents an individual email delivery attempt to a specific subscriber as part of an email campaign
 * 
 * Entity States (managed by workflow):
 * - PENDING: Initial state when delivery is queued
 * - SENT: Email has been sent to the email service
 * - DELIVERED: Email was successfully delivered to recipient's inbox
 * - OPENED: Recipient opened the email
 * - CLICKED: Recipient clicked a link in the email
 * - FAILED: Email delivery failed
 * - BOUNCED: Email bounced back
 */
@Data
public class EmailDelivery implements CyodaEntity {
    public static final String ENTITY_NAME = EmailDelivery.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String id;
    
    // Required core business fields
    private String campaignId;
    private String subscriberId;
    private String emailAddress;
    
    // Optional fields for additional business data
    private LocalDateTime sentDate;
    private String deliveryStatus;
    private String errorMessage;
    private LocalDateTime openedDate;
    private LocalDateTime clickedDate;

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
        if (subscriberId == null || subscriberId.trim().isEmpty()) {
            return false;
        }
        if (emailAddress == null || emailAddress.trim().isEmpty()) {
            return false;
        }
        
        // Basic email format validation
        if (!emailAddress.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            return false;
        }
        
        // Validate sent date is not in the future
        if (sentDate != null && sentDate.isAfter(LocalDateTime.now())) {
            return false;
        }
        
        // Validate opened date is after sent date if both are present
        if (sentDate != null && openedDate != null && openedDate.isBefore(sentDate)) {
            return false;
        }
        
        // Validate clicked date is after opened date if both are present
        if (openedDate != null && clickedDate != null && clickedDate.isBefore(openedDate)) {
            return false;
        }
        
        return true;
    }
}
