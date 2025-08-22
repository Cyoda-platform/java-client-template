package com.java_template.application.processor;

import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class InventorySyncProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(InventorySyncProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public InventorySyncProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Product for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Product.class)
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
        if (entity == null) return null;

        try {
            // Normalize textual fields
            if (entity.getName() != null) {
                entity.setName(entity.getName().trim());
            }
            if (entity.getDescription() == null) {
                entity.setDescription("");
            } else {
                entity.setDescription(entity.getDescription().trim());
            }
            if (entity.getCurrency() != null) {
                entity.setCurrency(entity.getCurrency().trim().toUpperCase());
            }

            // Normalize inventory quantity
            if (entity.getAvailableQuantity() == null) {
                entity.setAvailableQuantity(0);
            } else if (entity.getAvailableQuantity() < 0) {
                entity.setAvailableQuantity(0);
            }

            // Normalize price to 2 decimal places (HALF_UP)
            if (entity.getPrice() != null) {
                BigDecimal bd = BigDecimal.valueOf(entity.getPrice()).setScale(2, RoundingMode.HALF_UP);
                entity.setPrice(bd.doubleValue());
            }

            logger.info("InventorySyncProcessor completed normalization for product id={}", entity.getId());
        } catch (Exception ex) {
            logger.error("Error during InventorySyncProcessor for product id=" + (entity != null ? entity.getId() : "null"), ex);
            throw ex;
        }

        return entity;
    }
}