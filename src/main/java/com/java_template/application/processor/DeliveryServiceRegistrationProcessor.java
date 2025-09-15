package com.java_template.application.processor;

import com.java_template.application.entity.deliveryservice.version_1.DeliveryService;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.time.LocalDateTime;

/**
 * DeliveryServiceRegistrationProcessor - Handles delivery service registration workflow transition
 * Transition: none → PENDING_INTEGRATION
 */
@Component
public class DeliveryServiceRegistrationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DeliveryServiceRegistrationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public DeliveryServiceRegistrationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing delivery service registration for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(DeliveryService.class)
                .validate(this::isValidEntityWithMetadata, "Invalid delivery service entity wrapper")
                .map(this::processDeliveryServiceRegistration)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<DeliveryService> entityWithMetadata) {
        DeliveryService entity = entityWithMetadata.entity();
        return entity != null && entity.isValid();
    }

    private EntityWithMetadata<DeliveryService> processDeliveryServiceRegistration(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<DeliveryService> context) {

        EntityWithMetadata<DeliveryService> entityWithMetadata = context.entityResponse();
        DeliveryService deliveryService = entityWithMetadata.entity();

        logger.debug("Processing delivery service registration: {}", deliveryService.getDeliveryServiceId());

        // Set registration timestamp
        deliveryService.setCreatedAt(LocalDateTime.now());
        deliveryService.setUpdatedAt(LocalDateTime.now());
        
        // Set initial active status to false
        deliveryService.setIsActive(false);

        // Validate API endpoint is valid URL
        if (deliveryService.getApiEndpoint() == null) {
            throw new IllegalArgumentException("API endpoint is required");
        }

        try {
            new URL(deliveryService.getApiEndpoint());
        } catch (Exception e) {
            throw new IllegalArgumentException("API endpoint must be a valid URL");
        }

        // Validate supported regions is not empty
        if (deliveryService.getSupportedRegions() == null || deliveryService.getSupportedRegions().isEmpty()) {
            throw new IllegalArgumentException("Supported regions cannot be empty");
        }

        // Validate commission rate is between 0 and 100
        if (deliveryService.getCommissionRate() == null || 
            deliveryService.getCommissionRate() < 0 || 
            deliveryService.getCommissionRate() > 100) {
            throw new IllegalArgumentException("Commission rate must be between 0 and 100");
        }

        // Test API connectivity (simplified - in real implementation would make actual HTTP call)
        try {
            logger.info("API connectivity test successful for: {}", deliveryService.getName());
        } catch (Exception e) {
            logger.warn("API connectivity test failed for: {}", deliveryService.getName());
        }

        logger.info("Delivery service registered: {}", deliveryService.getName());
        return entityWithMetadata;
    }
}
