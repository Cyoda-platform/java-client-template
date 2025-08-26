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
public class ValidateProductProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateProductProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ValidateProductProcessor(SerializerFactory serializerFactory) {
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

        // Basic normalization and business-safe adjustments without changing entity semantics.
        try {
            // Normalize string fields (use existing setters/getters only)
            if (entity.getName() != null) {
                entity.setName(entity.getName().trim());
            }
            if (entity.getSku() != null) {
                entity.setSku(entity.getSku().trim());
            }
            if (entity.getDescription() != null) {
                entity.setDescription(entity.getDescription().trim());
            }

            // Ensure price has a sensible scale (round to 2 decimals)
            if (entity.getPrice() != null) {
                double price = entity.getPrice();
                double rounded = Math.round(price * 100.0) / 100.0;
                entity.setPrice(rounded);
            }

            // Enforce non-negative availableQuantity (should be validated by isValid, but double-check)
            if (entity.getAvailableQuantity() != null && entity.getAvailableQuantity() < 0) {
                // Defensive: set to zero if negative (avoid throwing here; validation already passed earlier)
                entity.setAvailableQuantity(0);
            }

            // Business decision: if no stock, ensure product is not active.
            // Activation step is handled by a separate ActivateProductProcessor, but it's safe to ensure inactive when no stock.
            if (entity.getAvailableQuantity() != null && entity.getAvailableQuantity() <= 0) {
                entity.setActive(false);
            }

            // If availableQuantity > 0 and active is false, do not force activation here;
            // ActivationProcessor will handle explicit activation. We keep current active state.
        } catch (Exception ex) {
            logger.warn("Exception during product validation adjustments for sku={} : {}", entity.getSku(), ex.getMessage());
            // Do not rethrow; allow Cyoda to persist entity state as adjusted
        }

        return entity;
    }
}