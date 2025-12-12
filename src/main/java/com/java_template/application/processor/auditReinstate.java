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
 * Processor to audit customer reinstatement
 * 
 * This processor is triggered during the suspended -> active transition.
 * It creates an audit trail for the customer reinstatement process.
 */
@Component
public class auditReinstate implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(auditReinstate.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public auditReinstate(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Customer.class)
                .validate(this::isValidEntityWithMetadata, "Invalid customer entity wrapper")
                .map(this::processReinstateAudit)
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
     * Main business logic for auditing reinstatement
     */
    private EntityWithMetadata<Customer> processReinstateAudit(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Customer> context) {

        EntityWithMetadata<Customer> entityWithMetadata = context.entityResponse();
        Customer customer = entityWithMetadata.entity();

        logger.info("Creating reinstatement audit for customer: {}", customer.getCustomerId());

        // Initialize metadata if not present
        if (customer.getMetadata() == null) {
            customer.setMetadata(new HashMap<>());
        }

        Map<String, Object> metadata = customer.getMetadata();
        
        // Create audit record
        String auditId = "AUDIT-" + UUID.randomUUID().toString().substring(0, 8);
        
        Map<String, Object> reinstateAudit = new HashMap<>();
        reinstateAudit.put("auditId", auditId);
        reinstateAudit.put("action", "customer_reinstatement");
        reinstateAudit.put("performedAt", LocalDateTime.now().toString());
        reinstateAudit.put("performedBy", "system"); // In real implementation, would be actual user
        reinstateAudit.put("reason", "Issue resolved - customer reinstated");
        reinstateAudit.put("previousState", "suspended");
        reinstateAudit.put("newState", "active");
        reinstateAudit.put("approvalRequired", false);
        reinstateAudit.put("complianceChecked", true);
        
        // Add to audit trail
        metadata.put("reinstateAudit", reinstateAudit);

        // Clear suspension flags
        metadata.put("suspensionNotified", false);
        metadata.put("requiresReview", false);
        metadata.put("reinstated", true);
        metadata.put("reinstatedAt", LocalDateTime.now().toString());

        // Restore access flags
        metadata.put("accessGranted", true);
        metadata.put("accessRestoredAt", LocalDateTime.now().toString());

        // Update permissions if they exist
        if (metadata.containsKey("permissions")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> permissions = (Map<String, Object>) metadata.get("permissions");
            permissions.put("canViewProfile", true);
            permissions.put("canUpdateProfile", true);
            permissions.put("canRequestSupport", true);
            permissions.put("canAccessDashboard", true);
            permissions.put("accountTerminated", false);
        }

        // Update timestamps
        customer.setUpdatedAt(LocalDateTime.now());

        logger.info("Reinstatement audit created for customer {} with audit ID: {}", 
                   customer.getCustomerId(), auditId);

        return entityWithMetadata;
    }
}
