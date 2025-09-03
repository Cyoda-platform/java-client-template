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

import org.springframework.beans.factory.annotation.Autowired;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

@Component
public class ProductExtractionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ProductExtractionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    @Autowired
    private EntityService entityService;

    public ProductExtractionProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Product extraction for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Product.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
            })
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
        Product entity = context.entity();

        // Extract product data from Pet Store API
        // This is a simulation of the extraction process
        logger.info("Extracting product data for product: {}", entity.getName());

        // Set extraction metadata
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(LocalDateTime.now());
        }
        entity.setUpdatedAt(LocalDateTime.now());

        // Initialize sales metrics to 0 for new products
        if (entity.getSalesVolume() == null) {
            entity.setSalesVolume(0);
        }
        if (entity.getRevenue() == null) {
            entity.setRevenue(BigDecimal.ZERO);
        }

        // Ensure required fields are set
        if (entity.getPhotoUrls() == null) {
            entity.setPhotoUrls(new ArrayList<>());
        }
        if (entity.getTags() == null) {
            entity.setTags(new ArrayList<>());
        }
        if (entity.getStockQuantity() == null) {
            entity.setStockQuantity(0);
        }

        logger.info("Product extraction completed for: {}", entity.getName());
        return entity;
    }
}
