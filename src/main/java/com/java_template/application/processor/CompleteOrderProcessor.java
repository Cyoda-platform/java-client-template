package com.java_template.application.processor;

import com.java_template.application.entity.order.version_1.Order;
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
public class CompleteOrderProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CompleteOrderProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CompleteOrderProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Order for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Order.class)
            .validate(this::isValidEntity, "Invalid entity state for completion - order must be approved or in staff_review")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Order entity) {
        if (entity == null) return false;
        if (!entity.isValid()) return false;
        String status = entity.getStatus();
        if (status == null) return false;
        // Only complete orders that are approved or have completed staff review
        return "approved".equalsIgnoreCase(status) || "staff_review".equalsIgnoreCase(status);
    }

    private Order processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Order> context) {
        Order order = context.entity();
        logger.info("Completing order id={}, type={}, currentStatus={}", order.getId(), order.getType(), order.getStatus());

        // Business rules:
        // - Only approved or staff_review orders reach this processor (validated above).
        // - If type == "adopt" => finalization implies adoption for the pet.
        // - For other types (reserve/purchase) => mark as reserved.
        // NOTE: The Pet entity update (pet.status) should be handled by a separate processor or via entityService.
        // This processor finalizes the order status to 'completed' and records completion notes.
        String type = order.getType() != null ? order.getType().trim().toLowerCase() : "";

        // Set final order status
        order.setStatus("completed");

        // Add informational note about pet final status - actual pet update is out-of-band here
        String existingNotes = order.getNotes() != null ? order.getNotes() : "";
        StringBuilder notes = new StringBuilder(existingNotes);
        if (notes.length() > 0 && !notes.toString().endsWith(" ")) notes.append(" ");

        if ("adopt".equals(type)) {
            notes.append("Order completed: pet should be marked as 'adopted'.");
        } else {
            notes.append("Order completed: pet should be marked as 'reserved'.");
        }

        // Append processing detail
        notes.append(" Completed by CompleteOrderProcessor.");

        order.setNotes(notes.toString());
        logger.info("Order {} marked as completed. Notes updated.", order.getId());

        return order;
    }
}