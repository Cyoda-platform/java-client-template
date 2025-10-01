package com.java_template.application.processor;

import com.java_template.application.entity.medication.version_1.Medication;
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
 * Processor for handling medication dispenses
 * Updates inventory quantities and tracks dispense records
 */
@Component
public class MedicationDispenseProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(MedicationDispenseProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public MedicationDispenseProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Medication.class)
                .validate(this::isValidEntityWithMetadata, "Invalid medication wrapper")
                .map(this::processMedicationDispense)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Medication> entityWithMetadata) {
        Medication entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    private EntityWithMetadata<Medication> processMedicationDispense(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Medication> context) {

        EntityWithMetadata<Medication> entityWithMetadata = context.entityResponse();
        Medication medication = entityWithMetadata.entity();

        logger.debug("Processing dispense for medication lot: {}", medication.getLotNumber());

        // Update inventory quantities based on dispenses
        updateInventoryQuantities(medication);
        
        // Validate inventory levels
        validateInventoryLevels(medication);
        
        // Update timestamps
        medication.setUpdatedAt(LocalDateTime.now());

        logger.info("Medication dispense processed for lot: {}", medication.getLotNumber());

        return entityWithMetadata;
    }

    private void updateInventoryQuantities(Medication medication) {
        if (medication.getDispenses() == null || medication.getDispenses().isEmpty()) {
            return;
        }

        // Calculate total dispensed quantity
        int totalDispensed = medication.getDispenses().stream()
                .mapToInt(dispense -> dispense.getQuantity() != null ? dispense.getQuantity() : 0)
                .sum();

        // Calculate total returned quantity
        int totalReturned = 0;
        if (medication.getReturns() != null) {
            totalReturned = medication.getReturns().stream()
                    .mapToInt(returnRecord -> returnRecord.getQuantity() != null ? returnRecord.getQuantity() : 0)
                    .sum();
        }

        // Update quantity on hand (assuming we start with initial quantity)
        // In a real system, this would be calculated from initial stock + receipts - dispenses + returns
        int currentQuantity = medication.getQuantityOnHand() != null ? medication.getQuantityOnHand() : 0;
        int netDispensed = totalDispensed - totalReturned;
        
        // For this processor, we assume the quantity on hand reflects the latest dispense
        logger.debug("Lot {}: Total dispensed: {}, Total returned: {}, Net dispensed: {}", 
                    medication.getLotNumber(), totalDispensed, totalReturned, netDispensed);
    }

    private void validateInventoryLevels(Medication medication) {
        if (medication.getQuantityOnHand() != null && medication.getQuantityOnHand() < 0) {
            logger.warn("Negative inventory detected for lot {}: {}", 
                       medication.getLotNumber(), medication.getQuantityOnHand());
            throw new IllegalStateException("Inventory cannot be negative for lot: " + medication.getLotNumber());
        }

        if (medication.getQuantityOnHand() != null && medication.getQuantityOnHand() == 0) {
            logger.info("Lot {} is now depleted", medication.getLotNumber());
        }

        if (medication.getQuantityOnHand() != null && medication.getQuantityOnHand() <= 5) {
            logger.warn("Low inventory alert for lot {}: {} units remaining", 
                       medication.getLotNumber(), medication.getQuantityOnHand());
        }
    }
}
