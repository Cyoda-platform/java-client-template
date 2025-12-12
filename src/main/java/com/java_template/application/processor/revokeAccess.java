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
 * Processor to revoke access for customers being terminated
 * 
 * This processor is triggered during the termination_pending -> terminated transition.
 * It revokes all access permissions and disables customer accounts.
 */
@Component
public class revokeAccess implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(revokeAccess.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public revokeAccess(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Customer.class)
                .validate(this::isValidEntityWithMetadata, "Invalid customer entity wrapper")
                .map(this::processAccessRevocation)
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
     * Main business logic for revoking access
     */
    private EntityWithMetadata<Customer> processAccessRevocation(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Customer> context) {

        EntityWithMetadata<Customer> entityWithMetadata = context.entityResponse();
        Customer customer = entityWithMetadata.entity();

        logger.info("Revoking access for customer: {}", customer.getCustomerId());

        // Initialize metadata if not present
        if (customer.getMetadata() == null) {
            customer.setMetadata(new HashMap<>());
        }

        Map<String, Object> metadata = customer.getMetadata();
        
        // Revoke all access permissions
        metadata.put("accessGranted", false);
        metadata.put("accessRevokedAt", LocalDateTime.now().toString());
        metadata.put("terminationReason", "Customer termination process");
        metadata.put("accountDisabled", true);
        
        // Clear all permissions
        Map<String, Object> permissions = new HashMap<>();
        permissions.put("canViewProfile", false);
        permissions.put("canUpdateProfile", false);
        permissions.put("canRequestSupport", false);
        permissions.put("canAccessDashboard", false);
        permissions.put("accountTerminated", true);
        metadata.put("permissions", permissions);

        // Add termination audit trail
        Map<String, Object> terminationAudit = new HashMap<>();
        terminationAudit.put("accessRevokedAt", LocalDateTime.now().toString());
        terminationAudit.put("revokedBy", "system");
        terminationAudit.put("reason", "Customer termination workflow");
        metadata.put("terminationAudit", terminationAudit);

        // Update timestamps
        customer.setUpdatedAt(LocalDateTime.now());

        logger.info("Access revoked for customer {} - account disabled", 
                   customer.getCustomerId());

        return entityWithMetadata;
    }
}
