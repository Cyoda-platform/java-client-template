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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Component
public class StoreInventoryItemProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StoreInventoryItemProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public StoreInventoryItemProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing InventoryItem for request: {}", request.getId());

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

        if (entity == null) {
            logger.warn("Received null InventoryItem in execution context");
            return null;
        }

        String status = entity.getStatus();
        if (status == null) {
            logger.warn("InventoryItem {} has null status - marking as INVALID", entity.getId());
            entity.setStatus("INVALID");
            return entity;
        }

        // If already marked invalid, do nothing further
        if ("INVALID".equalsIgnoreCase(status)) {
            logger.info("InventoryItem {} is INVALID - skipping store", entity.getId());
            return entity;
        }

        // Only store items that passed validation/enrichment
        if (!"VALIDATED".equalsIgnoreCase(status) && !"ENRICHED".equalsIgnoreCase(status) && !"INGESTED".equalsIgnoreCase(status)) {
            // If status is some unexpected value, log and mark INVALID to avoid storing inconsistent data
            logger.warn("InventoryItem {} has unexpected status '{}' - marking as INVALID", entity.getId(), status);
            entity.setStatus("INVALID");
            return entity;
        }

        // Normalization: trim textual fields if present
        if (entity.getName() != null) entity.setName(entity.getName().trim());
        if (entity.getCategory() != null) entity.setCategory(entity.getCategory().trim());
        if (entity.getLocation() != null) entity.setLocation(entity.getLocation().trim());
        if (entity.getSupplier() != null) entity.setSupplier(entity.getSupplier().trim());

        // Ensure dateAdded exists; set to current date if missing
        if (entity.getDateAdded() == null || entity.getDateAdded().isBlank()) {
            entity.setDateAdded(LocalDate.now().toString());
        }

        // Normalize price to 2 decimal places
        if (entity.getPrice() != null) {
            try {
                BigDecimal bd = BigDecimal.valueOf(entity.getPrice()).setScale(2, RoundingMode.HALF_UP);
                entity.setPrice(bd.doubleValue());
            } catch (Exception ex) {
                logger.warn("Failed to normalize price for InventoryItem {}: {}", entity.getId(), ex.getMessage());
            }
        }

        // Final state transition: mark as STORED
        entity.setStatus("STORED");
        logger.info("InventoryItem {} stored (status set to STORED)", entity.getId());

        return entity;
    }
}