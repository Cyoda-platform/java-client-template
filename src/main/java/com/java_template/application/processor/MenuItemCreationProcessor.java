package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.menuitem.version_1.MenuItem;
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
 * MenuItemCreationProcessor - Handles menu item creation workflow transition
 * Transition: none → DRAFT
 */
@Component
public class MenuItemCreationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(MenuItemCreationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public MenuItemCreationProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing menu item creation for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(MenuItem.class)
                .validate(this::isValidEntityWithMetadata, "Invalid menu item entity wrapper")
                .map(this::processMenuItemCreation)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<MenuItem> entityWithMetadata) {
        MenuItem entity = entityWithMetadata.entity();
        return entity != null && entity.isValid();
    }

    private EntityWithMetadata<MenuItem> processMenuItemCreation(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<MenuItem> context) {

        EntityWithMetadata<MenuItem> entityWithMetadata = context.entityResponse();
        MenuItem menuItem = entityWithMetadata.entity();

        logger.debug("Processing menu item creation: {}", menuItem.getMenuItemId());

        // Set creation timestamp
        menuItem.setCreatedAt(LocalDateTime.now());
        menuItem.setUpdatedAt(LocalDateTime.now());
        
        // Set initial availability to false (draft state)
        menuItem.setIsAvailable(false);

        // Validate price is positive
        if (menuItem.getPrice() == null || menuItem.getPrice() <= 0) {
            throw new IllegalArgumentException("Menu item price must be greater than 0");
        }

        // Validate restaurant exists and is active
        validateRestaurantExists(menuItem.getRestaurantId());

        logger.info("Menu item created: {}", menuItem.getName());
        return entityWithMetadata;
    }

    private void validateRestaurantExists(String restaurantId) {
        try {
            ModelSpec restaurantModelSpec = new ModelSpec()
                    .withName(Restaurant.ENTITY_NAME)
                    .withVersion(Restaurant.ENTITY_VERSION);

            SimpleCondition restaurantCondition = new SimpleCondition()
                    .withJsonPath("$.restaurantId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(restaurantId));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(restaurantCondition));

            List<EntityWithMetadata<Restaurant>> restaurants = entityService.search(restaurantModelSpec, condition, Restaurant.class);

            if (restaurants.isEmpty()) {
                throw new IllegalArgumentException("Restaurant with ID " + restaurantId + " not found");
            }

            Restaurant restaurant = restaurants.get(0).entity();
            String restaurantState = restaurants.get(0).getState();
            
            if (!"ACTIVE".equals(restaurantState)) {
                throw new IllegalArgumentException("Cannot create menu item for inactive restaurant");
            }

        } catch (Exception e) {
            logger.error("Error validating restaurant {}: {}", restaurantId, e.getMessage());
            throw new IllegalArgumentException("Cannot create menu item for inactive restaurant");
        }
    }
}
