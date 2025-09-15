package com.java_template.application.processor;

import com.java_template.application.entity.menuitem.version_1.MenuItem;
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
 * MenuItemPublishProcessor - Handles menu item publishing workflow transition
 * Transition: DRAFT → ACTIVE
 */
@Component
public class MenuItemPublishProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(MenuItemPublishProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public MenuItemPublishProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing menu item publish for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(MenuItem.class)
                .validate(this::isValidEntityWithMetadata, "Invalid menu item entity wrapper")
                .map(this::processMenuItemPublish)
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

    private EntityWithMetadata<MenuItem> processMenuItemPublish(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<MenuItem> context) {

        EntityWithMetadata<MenuItem> entityWithMetadata = context.entityResponse();
        MenuItem menuItem = entityWithMetadata.entity();

        logger.debug("Processing menu item publish: {}", menuItem.getMenuItemId());

        // Make item available
        menuItem.setIsAvailable(true);
        menuItem.setUpdatedAt(LocalDateTime.now());

        // Validate all required fields are complete
        if (menuItem.getName() == null || menuItem.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Menu item name cannot be empty");
        }

        if (menuItem.getCategory() == null || menuItem.getCategory().trim().isEmpty()) {
            throw new IllegalArgumentException("Menu item category cannot be empty");
        }

        if (menuItem.getPrice() == null || menuItem.getPrice() <= 0) {
            throw new IllegalArgumentException("Menu item price must be greater than 0");
        }

        // Note: In a real implementation, we would notify delivery services about new menu item
        logger.info("Menu item published: {}", menuItem.getName());
        
        return entityWithMetadata;
    }
}
