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
public class ProductIndexProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ProductIndexProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ProductIndexProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Product for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Product.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
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
            // Normalize textual fields
            if (product.getName() != null) {
                product.setName(product.getName().trim());
            }
            if (product.getSku() != null) {
                product.setSku(product.getSku().trim());
            }
            if (product.getCurrency() != null) {
                product.setCurrency(product.getCurrency().trim().toUpperCase());
            }
            if (product.getDescription() != null) {
                product.setDescription(product.getDescription().trim());
            } else {
                product.setDescription("");
            }

            // Normalize numeric fields: price and stock
            if (product.getPrice() == null || product.getPrice() < 0.0) {
                logger.warn("Product {} has invalid price (null or negative). Normalizing to 0.0", product.getId());
                product.setPrice(0.0);
            }

            if (product.getStock() == null || product.getStock() < 0) {
                logger.warn("Product {} has invalid stock (null or negative). Normalizing to 0", product.getId());
                product.setStock(product.getStock() == null ? 0 : Math.max(0, product.getStock()));
            }

            // Compute availability from stock if not explicitly set or to enforce consistency
            boolean computedAvailable = product.getStock() != null && product.getStock() > 0;
            // Always align available flag with current stock state
            product.setAvailable(computedAvailable);

            // Prepare a simple catalog document (stringified JSON) to be used by downstream indexer
            StringBuilder catalogDoc = new StringBuilder();
            catalogDoc.append("{")
                .append("\"id\":\"").append(product.getId()).append("\",")
                .append("\"name\":\"").append(product.getName()).append("\",")
                .append("\"sku\":\"").append(product.getSku()).append("\",")
                .append("\"price\":").append(product.getPrice()).append(",")
                .append("\"currency\":\"").append(product.getCurrency()).append("\",")
                .append("\"available\":").append(product.getAvailable()).append(",")
                .append("\"stock\":").append(product.getStock())
                .append("}");

            // In a real implementation we would call an indexing service or emit an event.
            // Here we only log the prepared document so the workflow can pick it up or an external
            // integration can be implemented later.
            logger.info("Prepared catalog document for product {}: {}", product.getId(), catalogDoc.toString());

        } catch (Exception ex) {
            logger.error("Failed to prepare catalog document for product {}: {}", product != null ? product.getId() : "unknown", ex.getMessage(), ex);
            // Do not throw; keep processor tolerant. The entity state will still be persisted.
        }

        return product;
    }
}