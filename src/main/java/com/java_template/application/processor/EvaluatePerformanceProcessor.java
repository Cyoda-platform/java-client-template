package com.java_template.application.processor;

import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

@Component
public class EvaluatePerformanceProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EvaluatePerformanceProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public EvaluatePerformanceProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Product for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Product.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
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

        // Business rules:
        // 1. If inventory_on_hand <= REORDER_THRESHOLD -> RESTOCK
        // 2. Else if total_sales_volume < UNDERPERFORMING_SALES_THRESHOLD -> UNDERPERFORMING
        // 3. Else -> OK
        // 4. Update lastUpdated timestamp

        final int REORDER_THRESHOLD = 10; // items at or below this should be flagged for restock
        final int UNDERPERFORMING_SALES_THRESHOLD = 5; // low sales volume threshold

        try {
            Integer inventory = entity.getInventoryOnHand();
            Integer salesVolume = entity.getTotalSalesVolume();

            // Priority: RESTOCK over UNDERPERFORMING
            if (inventory != null && inventory <= REORDER_THRESHOLD) {
                entity.setPerformanceFlag("RESTOCK");
                logger.info("Product {} flagged as RESTOCK (inventoryOnHand={})", entity.getProductId(), inventory);
            } else if (salesVolume != null && salesVolume < UNDERPERFORMING_SALES_THRESHOLD) {
                entity.setPerformanceFlag("UNDERPERFORMING");
                logger.info("Product {} flagged as UNDERPERFORMING (totalSalesVolume={})", entity.getProductId(), salesVolume);
            } else {
                entity.setPerformanceFlag("OK");
                logger.info("Product {} performance OK (sales={}, inventory={})", entity.getProductId(), salesVolume, inventory);
            }

            // Update lastUpdated timestamp to current instant in ISO-8601
            entity.setLastUpdated(Instant.now().toString());

        } catch (Exception ex) {
            // In case of unexpected issues, log and leave entity state unchanged except timestamp update
            logger.error("Error while evaluating performance for product {}: {}", entity.getProductId(), ex.getMessage(), ex);
            entity.setLastUpdated(Instant.now().toString());
        }

        return entity;
    }
}