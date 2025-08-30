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

import org.springframework.beans.factory.annotation.Autowired;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class AddItemProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AddItemProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public AddItemProcessor(SerializerFactory serializerFactory) {
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

        // Defensive: ensure lines list exists
        List<Cart.Line> lines = entity.getLines();
        if (lines == null) {
            lines = new ArrayList<>();
            entity.setLines(lines);
        }

        // Recalculate totals and total items based on valid lines
        int totalItems = 0;
        double grandTotal = 0.0;

        for (Cart.Line line : lines) {
            if (line == null) continue;
            // Use line's isValid to determine if it should be included in totals
            try {
                if (!line.isValid()) {
                    continue;
                }
            } catch (Exception ex) {
                // If isValid throws for any reason, skip the line
                logger.warn("Skipping invalid line when recalculating totals: {}", ex.getMessage());
                continue;
            }

            Integer qty = line.getQty();
            Double price = line.getPrice();
            if (qty == null || price == null) continue;

            totalItems += qty;
            grandTotal += price * qty;
        }

        entity.setTotalItems(totalItems);
        entity.setGrandTotal(grandTotal);

        // Status transition: if NEW and now has items, move to ACTIVE
        String status = entity.getStatus();
        if ((status == null || status.isBlank())) {
            // if status missing, default to NEW then promote if needed
            status = "NEW";
            entity.setStatus(status);
        }
        if ("NEW".equalsIgnoreCase(status) && totalItems > 0) {
            entity.setStatus("ACTIVE");
        }

        // Update updatedAt timestamp
        entity.setUpdatedAt(Instant.now().toString());

        return entity;
    }
}