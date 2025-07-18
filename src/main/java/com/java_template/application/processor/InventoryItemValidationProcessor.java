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
public class InventoryItemValidationProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public InventoryItemValidationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("InventoryItemValidationProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Validating InventoryItem for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(InventoryItem.class)
                .withErrorHandler(this::handleInventoryItemError)
                .validate(InventoryItem::isValid, "Invalid InventoryItem state")
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "InventoryItemValidationProcessor".equals(modelSpec.operationName()) &&
                "inventoryItem".equals(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private ErrorInfo handleInventoryItemError(Throwable throwable, InventoryItem item) {
        logger.error("Error validating InventoryItem: {}", item != null ? item.getItemId() : "null", throwable);
        return new ErrorInfo("InventoryItemValidationError", throwable.getMessage());
    }
}
