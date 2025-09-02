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
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class ProductActivationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ProductActivationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ProductActivationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Product activation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Product.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
            })
            .validate(this::isValidProduct, "Invalid product state")
            .map(this::processProductActivation)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidProduct(Product product) {
        return product != null && product.isValid();
    }

    private Product processProductActivation(ProcessorSerializer.ProcessorEntityExecutionContext<Product> context) {
        Product product = context.entity();

        // Validate product has required fields (sku, name, price, category)
        if (product.getSku() == null || product.getSku().trim().isEmpty()) {
            throw new IllegalArgumentException("Product SKU is required");
        }
        if (product.getName() == null || product.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Product name is required");
        }
        if (product.getPrice() == null || product.getPrice() <= 0) {
            throw new IllegalArgumentException("Product price must be greater than 0");
        }
        if (product.getCategory() == null || product.getCategory().trim().isEmpty()) {
            throw new IllegalArgumentException("Product category is required");
        }

        // If quantityAvailable is null or negative, set to 0
        if (product.getQuantityAvailable() == null || product.getQuantityAvailable() < 0) {
            product.setQuantityAvailable(0);
        }

        // Set default warehouseId if not provided
        if (product.getWarehouseId() == null || product.getWarehouseId().trim().isEmpty()) {
            product.setWarehouseId("DEFAULT-WH");
        }

        logger.info("Product {} activated successfully", product.getSku());
        return product;
    }
}
