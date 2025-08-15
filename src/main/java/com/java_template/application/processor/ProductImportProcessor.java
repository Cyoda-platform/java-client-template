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

import java.math.BigDecimal;

@Component
public class ProductImportProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ProductImportProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ProductImportProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing product import for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Product.class)
            .validate(this::isValidEntity, "Invalid product entity for import")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Product product) {
        return product != null && product.getSku() != null && !product.getSku().isBlank();
    }

    private Product processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Product> context) {
        Product product = context.entity();
        try {
            // Validate required fields
            if (product.getPrice() == null || product.getPrice().compareTo(BigDecimal.ZERO) < 0) {
                logger.warn("Product {} has invalid price", product.getSku());
            }
            if (product.getStockQuantity() == null || product.getStockQuantity() < 0) {
                logger.warn("Product {} has invalid stockQuantity, setting to 0", product.getSku());
                product.setStockQuantity(0);
            }
            // Ensure currency is set
            if (product.getCurrency() == null || product.getCurrency().isBlank()) {
                product.setCurrency("USD");
            }
            // Set import source if not provided
            if (product.getImportSource() == null) {
                product.setImportSource("IMPORT");
            }
            logger.info("Imported product sku={}", product.getSku());
        } catch (Exception e) {
            logger.error("Error during product import for sku {}: {}", product != null ? product.getSku() : "<null>", e.getMessage());
        }
        return product;
    }
}
