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
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;

@Component
public class ProductValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ProductValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    @Autowired
    private EntityService entityService;

    public ProductValidationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Product validation for request: {}", request.getId());

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

        logger.info("Validating product: {}", entity.getName());

        // Validate required fields
        if (entity.getName() == null || entity.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Product name is required");
        }
        if (entity.getId() == null || entity.getId() <= 0) {
            throw new IllegalArgumentException("Product ID must be valid");
        }
        if (entity.getPhotoUrls() == null || entity.getPhotoUrls().isEmpty()) {
            entity.setPhotoUrls(new ArrayList<>());
            entity.getPhotoUrls().add("https://example.com/default-product.jpg");
        }

        // Enrich product data
        if (entity.getPrice() == null || entity.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            // Set default price based on category average (simplified)
            entity.setPrice(new BigDecimal("100.00"));
        }

        // Normalize category names
        if (entity.getCategory() != null) {
            entity.setCategory(entity.getCategory().trim());
        }

        // Set default stock quantity if missing
        if (entity.getStockQuantity() == null) {
            entity.setStockQuantity(0);
        }

        // Calculate initial metrics for new products
        if (entity.getSalesVolume() == null) {
            entity.setSalesVolume(0);
        }
        if (entity.getRevenue() == null) {
            entity.setRevenue(BigDecimal.ZERO);
        }
        // lastSaleDate remains null for new products

        entity.setUpdatedAt(LocalDateTime.now());

        logger.info("Product validation completed for: {}", entity.getName());
        return entity;
    }
}
