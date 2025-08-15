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
        logger.info("Processing Product validation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Product.class)
            .validate(this::isValidEntity, "Invalid product payload")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Product product) {
        if (product == null) return false;
        if (product.getSku() == null || product.getSku().trim().isEmpty()) return false;
        if (product.getPrice() == null || product.getPrice().compareTo(java.math.BigDecimal.ZERO) < 0) return false;
        if (product.getStockQuantity() == null || product.getStockQuantity() < 0) return false;
        return true;
    }

    private Product processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Product> context) {
        Product product = context.entity();
        // Nothing more for validation processor
        logger.info("Product {} passed validation", product.getId());
        return product;
    }
}
