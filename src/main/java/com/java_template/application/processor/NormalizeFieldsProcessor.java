package com.java_template.application.processor;
import com.java_template.application.entity.inventoryitem.version_1.InventoryItem;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NormalizeFieldsProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NormalizeFieldsProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public NormalizeFieldsProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing InventoryItem normalization for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(InventoryItem.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(InventoryItem entity) {
        return entity != null && entity.isValid();
    }

    private InventoryItem processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<InventoryItem> context) {
        InventoryItem entity = context.entity();
        try {
            if (entity.getName() != null) {
                entity.setName(entity.getName().trim());
            }
            if (entity.getSku() != null) {
                entity.setSku(entity.getSku().trim().toUpperCase());
            }
            if (entity.getCategory() != null) {
                entity.setCategory(entity.getCategory().trim());
            }
            // Ensure negative quantities are corrected to zero
            if (entity.getQuantity() != null && entity.getQuantity() < 0) {
                logger.warn("Correcting negative quantity for item {}", entity.getTechnicalId());
                entity.setQuantity(0);
            }

            // After normalization move to VALIDATING
            entity.setStatus("VALIDATING");
        } catch (Exception e) {
            logger.error("Error during normalization for item {}: {}", entity.getTechnicalId(), e.getMessage(), e);
        }
        return entity;
    }
}
