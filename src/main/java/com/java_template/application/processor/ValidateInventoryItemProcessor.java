package com.java_template.application.processor;
import com.java_template.application.entity.inventoryitem.version_1.InventoryItem;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class ValidateInventoryItemProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateInventoryItemProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ValidateInventoryItemProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing InventoryItem for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(InventoryItem.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
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

        // Normalize textual fields
        if (entity.getName() != null) {
            entity.setName(entity.getName().trim());
        }
        if (entity.getCategory() != null) {
            entity.setCategory(entity.getCategory().trim());
        }
        if (entity.getLocation() != null) {
            entity.setLocation(entity.getLocation().trim());
        }
        if (entity.getSupplier() != null) {
            entity.setSupplier(entity.getSupplier().trim());
        }
        if (entity.getStatus() != null) {
            entity.setStatus(entity.getStatus().trim());
        }

        // Validation checks based on business rules and available getters/setters
        boolean valid = true;

        // Required string fields: id, name, category, status
        if (entity.getId() == null || entity.getId().isBlank()) {
            valid = false;
            logger.debug("InventoryItem validation failed: missing id");
        }
        if (entity.getName() == null || entity.getName().isBlank()) {
            valid = false;
            logger.debug("InventoryItem validation failed: missing name");
        }
        if (entity.getCategory() == null || entity.getCategory().isBlank()) {
            valid = false;
            logger.debug("InventoryItem validation failed: missing category");
        }
        if (entity.getStatus() == null || entity.getStatus().isBlank()) {
            valid = false;
            logger.debug("InventoryItem validation failed: missing status");
        }

        // Numeric validations: quantity and price must be present and non-negative
        if (entity.getQuantity() == null || entity.getQuantity() < 0) {
            valid = false;
            logger.debug("InventoryItem validation failed: invalid quantity");
        }
        if (entity.getPrice() == null || entity.getPrice() < 0.0) {
            valid = false;
            logger.debug("InventoryItem validation failed: invalid price");
        }

        // Set status according to validation result.
        if (valid) {
            entity.setStatus("VALIDATED");
            logger.info("InventoryItem {} validated successfully", entity.getId());
        } else {
            entity.setStatus("INVALID");
            logger.warn("InventoryItem {} marked as INVALID", entity.getId());
        }

        // Return modified entity — Cyoda will persist the entity state as part of the workflow
        return entity;
    }
}