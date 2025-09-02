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
public class ProductDiscontinuationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ProductDiscontinuationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ProductDiscontinuationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Product discontinuation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Product.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
            })
            .validate(this::isValidProduct, "Invalid product state")
            .map(this::processProductDiscontinuation)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidProduct(Product product) {
        return product != null && product.isValid();
    }

    private Product processProductDiscontinuation(ProcessorSerializer.ProcessorEntityExecutionContext<Product> context) {
        Product product = context.entity();

        // Validate product is in ACTIVE state (this would be checked by workflow state)
        String currentState = context.request().getPayload().getMeta().get("state").toString();
        if (!"ACTIVE".equals(currentState)) {
            throw new IllegalStateException("Product must be in ACTIVE state to discontinue");
        }

        // Check if product has pending orders - log warning if they exist
        // Note: In a real implementation, we would query for pending orders
        logger.warn("Discontinuing product {} - please ensure no pending orders exist", product.getSku());

        // Set quantityAvailable to 0
        product.setQuantityAvailable(0);

        logger.info("Product {} discontinued successfully", product.getSku());
        return product;
    }
}
