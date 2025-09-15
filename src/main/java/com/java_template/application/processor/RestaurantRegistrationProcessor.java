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
import java.util.List;

/**
 * RestaurantRegistrationProcessor - Handles restaurant registration workflow transition
 * Transition: none → PENDING_APPROVAL
 */
@Component
public class RestaurantRegistrationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RestaurantRegistrationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public RestaurantRegistrationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing restaurant registration for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Restaurant.class)
                .validate(this::isValidEntityWithMetadata, "Invalid restaurant entity wrapper")
                .map(this::processRestaurantRegistration)
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

    private EntityWithMetadata<Restaurant> processRestaurantRegistration(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Restaurant> context) {

        EntityWithMetadata<Restaurant> entityWithMetadata = context.entityResponse();
        Restaurant restaurant = entityWithMetadata.entity();

        logger.debug("Processing restaurant registration: {}", restaurant.getRestaurantId());

        // Set registration timestamp
        restaurant.setCreatedAt(LocalDateTime.now());
        restaurant.setUpdatedAt(LocalDateTime.now());
        
        // Set initial values
        restaurant.setIsActive(false);
        restaurant.setTotalOrders(0);
        restaurant.setRating(0.0);

        // Validate address is complete
        if (restaurant.getAddress() == null || 
            restaurant.getAddress().getLine1() == null || 
            restaurant.getAddress().getCity() == null ||
            restaurant.getAddress().getState() == null ||
            restaurant.getAddress().getPostcode() == null ||
            restaurant.getAddress().getCountry() == null) {
            throw new IllegalArgumentException("Restaurant address must be complete");
        }

        // Validate contact information
        if (restaurant.getContact() == null ||
            restaurant.getContact().getPhone() == null ||
            restaurant.getContact().getEmail() == null) {
            throw new IllegalArgumentException("Restaurant contact information must be valid");
        }

        logger.info("Restaurant registered: {}", restaurant.getName());
        return entityWithMetadata;
    }
}
