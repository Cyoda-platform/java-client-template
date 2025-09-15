package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.deliveryservice.version_1.DeliveryService;
import com.java_template.application.entity.restaurant.version_1.Restaurant;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DeliveryServiceActivationProcessor - Handles delivery service activation workflow transition
 * Transition: PENDING_INTEGRATION → ACTIVE
 */
@Component
public class DeliveryServiceActivationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DeliveryServiceActivationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public DeliveryServiceActivationProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing delivery service activation for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(DeliveryService.class)
                .validate(this::isValidEntityWithMetadata, "Invalid delivery service entity wrapper")
                .map(this::processDeliveryServiceActivation)
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

    private EntityWithMetadata<DeliveryService> processDeliveryServiceActivation(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<DeliveryService> context) {

        EntityWithMetadata<DeliveryService> entityWithMetadata = context.entityResponse();
        DeliveryService deliveryService = entityWithMetadata.entity();

        logger.debug("Processing delivery service activation: {}", deliveryService.getDeliveryServiceId());

        // Activate delivery service
        deliveryService.setIsActive(true);
        deliveryService.setUpdatedAt(LocalDateTime.now());

        // Setup webhook endpoints (simplified - in real implementation would configure actual webhooks)
        logger.info("Configuring webhook endpoints for delivery service: {}", deliveryService.getName());

        // Sync with existing restaurants in supported regions
        syncWithExistingRestaurants(deliveryService);

        logger.info("Delivery service activated: {}", deliveryService.getName());
        
        return entityWithMetadata;
    }

    private void syncWithExistingRestaurants(DeliveryService deliveryService) {
        try {
            ModelSpec restaurantModelSpec = new ModelSpec()
                    .withName(Restaurant.ENTITY_NAME)
                    .withVersion(Restaurant.ENTITY_VERSION);

            // Find all active restaurants
            List<EntityWithMetadata<Restaurant>> restaurants = entityService.findAll(restaurantModelSpec, Restaurant.class);

            for (EntityWithMetadata<Restaurant> restaurantWithMetadata : restaurants) {
                if (!"ACTIVE".equals(restaurantWithMetadata.getState())) {
                    continue;
                }

                Restaurant restaurant = restaurantWithMetadata.entity();
                
                // Check if restaurant is in supported regions
                if (restaurant.getAddress() != null && restaurant.getAddress().getCity() != null) {
                    String restaurantCity = restaurant.getAddress().getCity();
                    
                    if (deliveryService.getSupportedRegions().contains(restaurantCity)) {
                        // In a real implementation, we would send restaurant sync notification to delivery service
                        logger.info("Syncing restaurant {} with delivery service {}", 
                                restaurant.getName(), deliveryService.getName());
                    }
                }
            }

        } catch (Exception e) {
            logger.error("Error syncing restaurants with delivery service {}: {}", 
                    deliveryService.getName(), e.getMessage());
        }
    }
}
