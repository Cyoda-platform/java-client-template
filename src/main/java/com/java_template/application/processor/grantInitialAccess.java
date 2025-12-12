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
 * Processor to grant initial access to verified customers
 * 
 * This processor is triggered during the verified -> active transition.
 * It sets up initial access permissions and resources for the newly verified customer.
 */
@Component
public class grantInitialAccess implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(grantInitialAccess.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public grantInitialAccess(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Customer.class)
                .validate(this::isValidEntityWithMetadata, "Invalid customer entity wrapper")
                .map(this::processInitialAccess)
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
     * Main business logic for granting initial access
     */
    private EntityWithMetadata<Customer> processInitialAccess(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Customer> context) {

        EntityWithMetadata<Customer> entityWithMetadata = context.entityResponse();
        Customer customer = entityWithMetadata.entity();

        logger.info("Granting initial access for customer: {}", customer.getCustomerId());

        // Initialize metadata if not present
        if (customer.getMetadata() == null) {
            customer.setMetadata(new HashMap<>());
        }

        Map<String, Object> metadata = customer.getMetadata();
        
        // Grant initial access permissions
        metadata.put("accessGranted", true);
        metadata.put("accessGrantedAt", LocalDateTime.now().toString());
        metadata.put("initialAccessLevel", "standard");
        metadata.put("welcomeEmailSent", false);
        metadata.put("onboardingCompleted", true);
        
        // Set up initial resources/permissions (simulated)
        Map<String, Object> permissions = new HashMap<>();
        permissions.put("canViewProfile", true);
        permissions.put("canUpdateProfile", true);
        permissions.put("canRequestSupport", true);
        permissions.put("canAccessDashboard", true);
        metadata.put("permissions", permissions);

        // Update verification status to completed
        if (customer.getVerification() != null) {
            customer.getVerification().setCompletedAt(LocalDateTime.now());
        }

        // Update timestamps
        customer.setUpdatedAt(LocalDateTime.now());

        logger.info("Initial access granted for customer {} with standard permissions", 
                   customer.getCustomerId());

        return entityWithMetadata;
    }
}
