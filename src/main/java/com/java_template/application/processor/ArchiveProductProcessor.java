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
public class ArchiveProductProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ArchiveProductProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ArchiveProductProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Product archive for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Product.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract product entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract product entity: " + error.getMessage());
            })
            .validate(this::isValidProductForArchive, "Invalid product state for archive")
            .map(this::processProductArchive)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidProductForArchive(Product product) {
        return product != null && product.getSku() != null;
    }

    private Product processProductArchive(ProcessorSerializer.ProcessorEntityExecutionContext<Product> context) {
        Product product = context.entity();

        // Add archive event to product events
        if (product.getEvents() == null) {
            product.setEvents(new java.util.ArrayList<>());
        }

        Product.Event archiveEvent = new Product.Event();
        archiveEvent.setEventType("ProductArchived");
        archiveEvent.setTimestamp(Instant.now().toString());
        product.getEvents().add(archiveEvent);

        logger.info("Archived product {} with SKU {}", product.getName(), product.getSku());

        return product;
    }
}