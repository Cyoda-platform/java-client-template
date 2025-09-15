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
import java.util.ArrayList;
import java.util.List;

/**
 * RestaurantApprovalProcessor - Handles restaurant approval workflow transition
 * Transition: PENDING_APPROVAL → ACTIVE
 */
@Component
public class RestaurantApprovalProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RestaurantApprovalProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public RestaurantApprovalProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing restaurant approval for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Restaurant.class)
                .validate(this::isValidEntityWithMetadata, "Invalid restaurant entity wrapper")
                .map(this::processRestaurantApproval)
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

    private EntityWithMetadata<Restaurant> processRestaurantApproval(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Restaurant> context) {

        EntityWithMetadata<Restaurant> entityWithMetadata = context.entityResponse();
        Restaurant restaurant = entityWithMetadata.entity();

        logger.debug("Processing restaurant approval: {}", restaurant.getRestaurantId());

        // Activate restaurant
        restaurant.setIsActive(true);
        restaurant.setUpdatedAt(LocalDateTime.now());

        // Create default delivery zones if not specified
        if (restaurant.getDeliveryZones() == null || restaurant.getDeliveryZones().isEmpty()) {
            Restaurant.DeliveryZone defaultZone = new Restaurant.DeliveryZone();
            defaultZone.setZoneName("Default Zone");
            defaultZone.setRadius(5.0); // 5km radius
            defaultZone.setAdditionalFee(0.0);
            
            List<Restaurant.DeliveryZone> deliveryZones = new ArrayList<>();
            deliveryZones.add(defaultZone);
            restaurant.setDeliveryZones(deliveryZones);
        }

        // Note: In a real implementation, we would notify delivery services about new restaurant
        // This would be done via external service calls or message queues
        logger.info("Restaurant approved and activated: {}", restaurant.getName());
        
        return entityWithMetadata;
    }
}
