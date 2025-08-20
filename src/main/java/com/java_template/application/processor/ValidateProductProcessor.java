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

import java.time.Instant;

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
            .validate(this::isValidEntity, "Invalid product state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Product product) {
        if (product == null) return false;
        try {
            if (product.getPrice() == null) return false;
            if (product.getPrice().doubleValue() < 0.0) return false;
            if (product.getQuantityAvailable() == null) return false;
            if (product.getQuantityAvailable() < 0) return false;
        } catch (Exception e) {
            logger.warn("Validation check failed due to exception", e);
            return false;
        }
        return true;
    }

    private Product processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Product> context) {
        Product product = context.entity();
        // update timestamp and ensure product becomes ready
        try {
            product.setUpdated_at(Instant.now().toString());
        } catch (Exception e) {
            logger.warn("Failed to set updatedAt on product", e);
        }
        // no persistence here: Cyoda will persist the changed entity
        return product;
    }
}
