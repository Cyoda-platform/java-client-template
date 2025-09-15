package com.java_template.application.processor;

import com.java_template.application.entity.restaurant.version_1.Restaurant;
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

import java.time.LocalDateTime;

/**
 * RestaurantReactivationProcessor - Handles restaurant reactivation workflow transition
 * Transition: SUSPENDED → ACTIVE
 */
@Component
public class RestaurantReactivationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RestaurantReactivationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public RestaurantReactivationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing restaurant reactivation for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Restaurant.class)
                .validate(this::isValidEntityWithMetadata, "Invalid restaurant entity wrapper")
                .map(this::processRestaurantReactivation)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Restaurant> entityWithMetadata) {
        Restaurant entity = entityWithMetadata.entity();
        return entity != null && entity.isValid();
    }

    private EntityWithMetadata<Restaurant> processRestaurantReactivation(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Restaurant> context) {

        EntityWithMetadata<Restaurant> entityWithMetadata = context.entityResponse();
        Restaurant restaurant = entityWithMetadata.entity();

        logger.debug("Processing restaurant reactivation: {}", restaurant.getRestaurantId());

        // Reactivate restaurant
        restaurant.setIsActive(true);
        restaurant.setUpdatedAt(LocalDateTime.now());

        // Note: In a real implementation, we would notify delivery services about restaurant reactivation
        logger.info("Restaurant reactivated: {}", restaurant.getName());
        
        return entityWithMetadata;
    }
}
