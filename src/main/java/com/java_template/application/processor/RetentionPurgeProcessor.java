package com.java_template.application.processor;

import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.concurrent.CompletableFuture;

@Component
public class RetentionPurgeProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RetentionPurgeProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public RetentionPurgeProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Running retention purge for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Product.class)
            .validate(this::isValidEntity, "Invalid Product state for purge")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Product entity) { return entity != null && entity.isValid(); }

    private Product processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Product> context) {
        Product product = context.entity();
        try {
            // Purge old snapshots beyond retention period. Prototype: if sales_history length > 50, trim to last 50
            if (product.getSales_history() != null && product.getSales_history().size() > 50) {
                int size = product.getSales_history().size();
                product.setSales_history(product.getSales_history().subList(size - 50, size));
                // Persist truncated product
                entityService.addItem(Product.ENTITY_NAME, String.valueOf(Product.ENTITY_VERSION), product).get();
                logger.info("Purged sales_history for product {} to last 50 entries", product.getProduct_id());
            }
        } catch (Exception ex) {
            logger.error("Error during retention purge for product {}: {}", product.getProduct_id(), ex.getMessage(), ex);
        }
        return product;
    }
}
