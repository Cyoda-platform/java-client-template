package com.java_template.application.processor;

import com.java_template.application.entity.InventoryItem;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.config.Config;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class InventoryItemTransformationProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public InventoryItemTransformationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("InventoryItemTransformationProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Transforming InventoryItem for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(InventoryItem.class)
                .withErrorHandler(this::handleInventoryItemError)
                .map(this::applyTransformation)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "InventoryItemTransformationProcessor".equals(modelSpec.operationName()) &&
                "inventoryItem".equals(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private InventoryItem applyTransformation(InventoryItem item) {
        // Example transformation: adjust price based on some business logic
        if (item.getPrice() > 100) {
            item.setPrice(item.getPrice() * 0.95); // apply 5% discount
            logger.info("Applied discount to InventoryItem: {}", item.getItemId());
        }
        return item;
    }

    private ErrorInfo handleInventoryItemError(Throwable throwable, InventoryItem item) {
        logger.error("Error transforming InventoryItem: {}", item != null ? item.getItemId() : "null", throwable);
        return new ErrorInfo("InventoryItemTransformationError", throwable.getMessage());
    }
}
