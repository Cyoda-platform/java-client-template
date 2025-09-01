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
public class StockMonitorProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StockMonitorProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public StockMonitorProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Product stock monitoring for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Product.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract product entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract product entity: " + error.getMessage());
            })
            .validate(this::isValidProductForStockMonitor, "Invalid product state for stock monitoring")
            .map(this::processStockMonitoring)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidProductForStockMonitor(Product product) {
        return product != null &&
               product.getSku() != null &&
               product.getQuantityAvailable() != null;
    }

    private Product processStockMonitoring(ProcessorSerializer.ProcessorEntityExecutionContext<Product> context) {
        Product product = context.entity();

        // Monitor stock levels and add appropriate events
        if (product.getEvents() == null) {
            product.setEvents(new java.util.ArrayList<>());
        }

        if (product.getQuantityAvailable() <= 0) {
            // Product is out of stock
            Product.Event outOfStockEvent = new Product.Event();
            outOfStockEvent.setEventType("ProductOutOfStock");
            outOfStockEvent.setTimestamp(Instant.now().toString());
            product.getEvents().add(outOfStockEvent);

            logger.warn("Product {} is out of stock (quantity: {})",
                       product.getSku(), product.getQuantityAvailable());
        } else if (product.getQuantityAvailable() <= 10) {
            // Low stock warning
            Product.Event lowStockEvent = new Product.Event();
            lowStockEvent.setEventType("ProductLowStock");
            lowStockEvent.setTimestamp(Instant.now().toString());
            product.getEvents().add(lowStockEvent);

            logger.warn("Product {} has low stock (quantity: {})",
                       product.getSku(), product.getQuantityAvailable());
        }

        logger.info("Stock monitoring completed for product {} (quantity: {})",
                   product.getSku(), product.getQuantityAvailable());

        return product;
    }
}