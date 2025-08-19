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
public class IndexItemProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(IndexItemProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public IndexItemProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing InventoryItem indexing for request: {}", request.getId());

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
            // This processor would normally push the item to a search index. We'll simulate by setting status READY when appropriate.
            if (entity.getStatus() != null && entity.getStatus().equals("VALIDATING")) {
                // If minimal required fields present mark as READY
                if (entity.getSku() != null && entity.getName() != null && entity.getQuantity() != null && entity.getSourceId() != null) {
                    entity.setStatus("READY");
                }
            }
        } catch (Exception e) {
            logger.error("Error during indexing for item {}: {}", entity.getTechnicalId(), e.getMessage(), e);
        }
        return entity;
    }
}
