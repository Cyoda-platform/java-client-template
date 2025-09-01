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
        logger.info("Processing Product validation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Product.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract product entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract product entity: " + error.getMessage());
            })
            .validate(this::isValidProductForValidation, "Invalid product state for validation")
            .map(this::processProductValidation)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidProductForValidation(Product product) {
        return product != null && product.getSku() != null;
    }

    private Product processProductValidation(ProcessorSerializer.ProcessorEntityExecutionContext<Product> context) {
        Product product = context.entity();

        // Perform comprehensive product validation
        validateRequiredFields(product);
        validateBusinessRules(product);
        validateDataQuality(product);

        logger.info("Product {} validation completed successfully", product.getSku());

        return product;
    }

    private void validateRequiredFields(Product product) {
        if (product.getSku() == null || product.getSku().isBlank()) {
            throw new IllegalArgumentException("Product SKU is required");
        }

        if (product.getName() == null || product.getName().isBlank()) {
            throw new IllegalArgumentException("Product name is required");
        }

        if (product.getPrice() == null || product.getPrice() < 0) {
            throw new IllegalArgumentException("Product price must be non-negative");
        }

        if (product.getQuantityAvailable() == null || product.getQuantityAvailable() < 0) {
            throw new IllegalArgumentException("Product quantity must be non-negative");
        }
    }

    private void validateBusinessRules(Product product) {
        // Validate category is set
        if (product.getCategory() == null || product.getCategory().isBlank()) {
            throw new IllegalArgumentException("Product category is required");
        }

        // Validate variants if present
        if (product.getVariants() != null) {
            for (Product.Variant variant : product.getVariants()) {
                if (variant.getVariantSku() == null || variant.getVariantSku().isBlank()) {
                    throw new IllegalArgumentException("Variant SKU is required for all variants");
                }
            }
        }
    }

    private void validateDataQuality(Product product) {
        // Check for reasonable price ranges
        if (product.getPrice() != null && product.getPrice() > 1000000) {
            logger.warn("Product {} has unusually high price: {}", product.getSku(), product.getPrice());
        }

        // Check for reasonable quantity
        if (product.getQuantityAvailable() != null && product.getQuantityAvailable() > 100000) {
            logger.warn("Product {} has unusually high quantity: {}", product.getSku(), product.getQuantityAvailable());
        }
    }
}