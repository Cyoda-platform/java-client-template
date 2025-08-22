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

import java.util.Objects;

@Component
public class ProductValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ProductValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ProductValidationProcessor(SerializerFactory serializerFactory) {
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
        Product product = context.entity();

        try {
            boolean valid = true;

            // Price validation: must be non-null and >= 0.0
            if (product.getPrice() == null || product.getPrice() < 0.0) {
                logger.warn("Product {} has invalid price: {}", product.getId(), product.getPrice());
                valid = false;
            }

            // SKU presence check
            String sku = product.getSku();
            if (sku == null || sku.isBlank()) {
                logger.warn("Product {} has missing SKU", product.getId());
                valid = false;
            }

            // Stock based availability: if stock is present and non-positive -> not available
            if (product.getStock() != null && product.getStock() <= 0) {
                logger.info("Product {} has non-positive stock ({}). Marking unavailable.", product.getId(), product.getStock());
                valid = false;
            }

            // Final availability decision
            product.setAvailable(valid);

            if (valid) {
                logger.info("Product {} validation PASSED. SKU='{}', price={}, stock={}", product.getId(), product.getSku(), product.getPrice(), product.getStock());
            } else {
                logger.info("Product {} validation FAILED. Setting available={}", product.getId(), product.getAvailable());
            }

        } catch (Exception ex) {
            logger.error("Unexpected error during ProductValidationProcessor for product {}: {}", product != null ? product.getId() : "unknown", ex.getMessage(), ex);
            // In case of unexpected error, be conservative and mark as unavailable
            if (product != null) {
                product.setAvailable(false);
            }
        }

        return product;
    }
}