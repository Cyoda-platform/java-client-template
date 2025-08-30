package com.java_template.application.processor;

import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.List;

@Component
public class CreateOnFirstAddProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreateOnFirstAddProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CreateOnFirstAddProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Cart for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Cart.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Cart entity) {
        return entity != null && entity.isValid();
    }

    private Cart processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Cart> context) {
        Cart entity = context.entity();
        if (entity == null) return null;

        try {
            String status = entity.getStatus();
            List<Cart.Line> lines = entity.getLines();

            // If no lines present, ensure totals are zero and leave status as-is (likely NEW)
            if (lines == null || lines.isEmpty()) {
                if (entity.getTotalItems() == null || entity.getTotalItems() != 0) {
                    entity.setTotalItems(0);
                }
                if (entity.getGrandTotal() == null || Double.compare(entity.getGrandTotal(), 0.0) != 0) {
                    entity.setGrandTotal(0.0);
                }
                // Do not force a transition; keep status as is (expected NEW) but ensure timestamps updated
                try {
                    entity.setUpdatedAt(OffsetDateTime.now().toString());
                } catch (Exception ex) {
                    logger.debug("Unable to set updatedAt on cart: {}", ex.getMessage());
                }
                return entity;
            }

            // Compute totals from lines
            int totalItems = 0;
            double grandTotal = 0.0;
            for (Cart.Line line : lines) {
                if (line == null) continue;
                Integer qty = line.getQty() != null ? line.getQty() : 0;
                Double price = line.getPrice() != null ? line.getPrice() : 0.0;
                totalItems += qty;
                grandTotal += qty * price;
            }
            entity.setTotalItems(totalItems);
            entity.setGrandTotal(grandTotal);

            // If this is the first add (cart NEW or not set), transition to ACTIVE and ensure createdAt
            if (status == null || status.isBlank()) {
                // If cart had no explicit status, treat this as first add -> ACTIVE
                entity.setStatus("ACTIVE");
            } else if ("NEW".equalsIgnoreCase(status)) {
                // If cart was NEW and now has at least one line, activate it
                entity.setStatus("ACTIVE");
            }

            // Ensure createdAt is set if missing (first persist might rely on controller but defend here)
            if (entity.getCreatedAt() == null || entity.getCreatedAt().isBlank()) {
                try {
                    entity.setCreatedAt(OffsetDateTime.now().toString());
                } catch (Exception ex) {
                    logger.debug("Unable to set createdAt on cart: {}", ex.getMessage());
                }
            }

            // Update timestamp
            entity.setUpdatedAt(OffsetDateTime.now().toString());

        } catch (Exception e) {
            logger.error("Error processing cart logic: {}", e.getMessage(), e);
        }

        return entity;
    }
}