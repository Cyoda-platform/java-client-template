package com.java_template.application.processor;

import com.java_template.application.entity.customer.version_1.Customer;
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

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Processor to notify support team when customer is suspended
 * 
 * This processor is triggered during the active -> suspended transition.
 * It sends notifications to the support team about the customer suspension.
 */
@Component
public class notifySupport implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(notifySupport.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public notifySupport(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Customer.class)
                .validate(this::isValidEntityWithMetadata, "Invalid customer entity wrapper")
                .map(this::processSupportNotification)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Customer> entityWithMetadata) {
        Customer customer = entityWithMetadata.entity();
        UUID technicalId = entityWithMetadata.metadata().getId();
        return customer != null && customer.isValid() && technicalId != null;
    }

    /**
     * Main business logic for notifying support team
     */
    private EntityWithMetadata<Customer> processSupportNotification(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Customer> context) {

        EntityWithMetadata<Customer> entityWithMetadata = context.entityResponse();
        Customer customer = entityWithMetadata.entity();

        logger.info("Notifying support team about customer suspension: {}", customer.getCustomerId());

        // Initialize metadata if not present
        if (customer.getMetadata() == null) {
            customer.setMetadata(new HashMap<>());
        }

        Map<String, Object> metadata = customer.getMetadata();
        
        // Create support notification record
        String notificationId = "SUPP-" + UUID.randomUUID().toString().substring(0, 8);
        
        Map<String, Object> supportNotification = new HashMap<>();
        supportNotification.put("notificationId", notificationId);
        supportNotification.put("type", "customer_suspension");
        supportNotification.put("priority", "medium");
        supportNotification.put("sentAt", LocalDateTime.now().toString());
        supportNotification.put("recipient", "support_team");
        supportNotification.put("channel", "email");
        supportNotification.put("status", "sent");
        
        // Add customer details for support team
        Map<String, Object> customerSummary = new HashMap<>();
        customerSummary.put("customerId", customer.getCustomerId());
        customerSummary.put("name", customer.getName());
        customerSummary.put("email", customer.getEmail());
        customerSummary.put("suspendedAt", LocalDateTime.now().toString());
        customerSummary.put("reason", "Manual suspension - requires review");
        
        supportNotification.put("customerSummary", customerSummary);
        
        metadata.put("supportNotification", supportNotification);

        // Add suspension tracking
        metadata.put("suspensionNotified", true);
        metadata.put("suspensionNotifiedAt", LocalDateTime.now().toString());
        metadata.put("requiresReview", true);

        // Update timestamps
        customer.setUpdatedAt(LocalDateTime.now());

        logger.info("Support team notified about customer {} suspension with notification ID: {}", 
                   customer.getCustomerId(), notificationId);

        return entityWithMetadata;
    }
}
