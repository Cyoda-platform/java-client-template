package com.java_template.application.processor;

import com.java_template.application.entity.product.version_1.Product;
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

@Component
public class ProductActivateProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ProductActivateProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ProductActivateProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Product for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Product.class)
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

    private boolean isValidEntity(Product entity) {
        return entity != null && entity.isValid();
    }

    private Product processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Product> context) {
        Product entity = context.entity();

        // Business logic for activating a product:
        // - Activation is a manual action that ensures the product is available for browsing/sale.
        // - In this model there is no explicit "status" field on Product; availability is derived from availableQuantity.
        // - When activating, ensure availableQuantity is at least 1 so the product becomes "active" for inventory checks.
        // - Do not modify or persist other entities here. The platform will persist changes to this entity automatically.
        try {
            logger.info("Activating product id={} sku={}", entity.getId(), entity.getSku());

            Integer qty = entity.getAvailableQuantity();
            if (qty == null || qty <= 0) {
                // If quantity is missing or zero, set a minimal positive stock to mark product active.
                entity.setAvailableQuantity(1);
                logger.info("Product id={} had non-positive availableQuantity ({}). Set to 1 to activate.", entity.getId(), qty);
            } else {
                logger.info("Product id={} already has availableQuantity={}. No inventory change required.", entity.getId(), qty);
            }

            // Ensure price is non-negative — entity.isValid() already enforced this,
            // but perform a safety check and log if unexpected value present.
            Double price = entity.getPrice();
            if (price == null || price < 0.0) {
                // Do not attempt to "fix" price here because validation passed before.
                logger.warn("Product id={} has invalid price ({}). Activation proceeded but consider correcting price via update.", entity.getId(), price);
            }

        } catch (Exception ex) {
            logger.error("Error while activating product id={}: {}", entity != null ? entity.getId() : "null", ex.getMessage(), ex);
            // In case of internal error, return entity unchanged; serializer will complete and could emit error info earlier.
        }

        return entity;
    }
}