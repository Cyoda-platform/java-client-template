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

import java.time.Instant;

@Component
public class CreateProductProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreateProductProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CreateProductProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Product creation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Product.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract product entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract product entity: " + error.getMessage());
            })
            .validate(this::isValidProductForCreation, "Invalid product state for creation")
            .map(this::processProductCreation)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidProductForCreation(Product product) {
        return product != null &&
               product.getSku() != null &&
               !product.getSku().isBlank() &&
               product.getName() != null &&
               !product.getName().isBlank() &&
               product.getPrice() != null &&
               product.getPrice() >= 0;
    }

    private Product processProductCreation(ProcessorSerializer.ProcessorEntityExecutionContext<Product> context) {
        Product product = context.entity();

        // Initialize default values if not set
        if (product.getQuantityAvailable() == null) {
            product.setQuantityAvailable(0);
        }

        if (product.getCategory() == null || product.getCategory().isBlank()) {
            product.setCategory("uncategorized");
        }

        // Add creation event to product events
        if (product.getEvents() == null) {
            product.setEvents(new java.util.ArrayList<>());
        }

        Product.Event creationEvent = new Product.Event();
        creationEvent.setEventType("ProductCreated");
        creationEvent.setTimestamp(Instant.now().toString());
        product.getEvents().add(creationEvent);

        logger.info("Created product {} with SKU {} in category {}",
                   product.getName(), product.getSku(), product.getCategory());

        return product;
    }
}