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
 * MenuItemAvailabilityProcessor - Handles menu item availability restoration workflow transition
 * Transition: UNAVAILABLE → ACTIVE
 */
@Component
public class MenuItemAvailabilityProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(MenuItemAvailabilityProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public MenuItemAvailabilityProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing menu item availability restoration for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(MenuItem.class)
                .validate(this::isValidEntityWithMetadata, "Invalid menu item entity wrapper")
                .map(this::processMenuItemAvailability)
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

    private EntityWithMetadata<MenuItem> processMenuItemAvailability(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<MenuItem> context) {

        EntityWithMetadata<MenuItem> entityWithMetadata = context.entityResponse();
        MenuItem menuItem = entityWithMetadata.entity();

        logger.debug("Processing menu item availability restoration: {}", menuItem.getMenuItemId());

        // Make item available again
        menuItem.setIsAvailable(true);
        menuItem.setUpdatedAt(LocalDateTime.now());

        logger.info("Menu item made available: {}", menuItem.getName());
        
        return entityWithMetadata;
    }
}
