package com.java_template.application.processor;

import com.java_template.application.entity.product.version_1.Product;
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
public class ProductAvailableProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ProductAvailableProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ProductAvailableProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Product for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Product.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Product entity) {
        return entity != null && entity.isValid();
    }

    private Product processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Product> context) {
        Product entity = context.entity();

        // Normalize SKU: trim and uppercase for consistent lookups/display
        if (entity.getSku() != null) {
            entity.setSku(entity.getSku().trim().toUpperCase());
        }

        // Normalize name: trim whitespace
        if (entity.getName() != null) {
            entity.setName(entity.getName().trim());
        }

        // Ensure price rounded to 2 decimal places (store as Double)
        if (entity.getPrice() != null) {
            double rounded = Math.round(entity.getPrice() * 100.0) / 100.0;
            entity.setPrice(rounded);
        }

        // If product is out of stock, annotate description with a single "(Out of stock)" marker.
        // If product returns to stock, remove the marker if present.
        Integer qty = entity.getQuantityAvailable();
        String desc = entity.getDescription();
        final String marker = "(Out of stock)";
        if (qty != null && qty.intValue() == 0) {
            if (desc == null || desc.isBlank()) {
                entity.setDescription(marker);
            } else if (!desc.contains(marker)) {
                entity.setDescription(desc + " " + marker);
            }
        } else {
            if (desc != null && desc.contains(marker)) {
                // remove the marker and tidy extra whitespace
                String cleaned = desc.replace(marker, "").trim();
                entity.setDescription(cleaned.isEmpty() ? null : cleaned);
            }
        }

        // No external entity updates are performed here. Cyoda will persist changes to this entity automatically.
        return entity;
    }
}