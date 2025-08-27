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
public class PersistProductProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PersistProductProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    // Simple business thresholds - chosen conservatively; adjust via configuration if needed
    private static final int DEFAULT_REORDER_THRESHOLD = 10;
    private static final int DEFAULT_LOW_SALES_THRESHOLD = 20;

    public PersistProductProcessor(SerializerFactory serializerFactory) {
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
        try {
            // Business logic:
            // 1. Evaluate performance flag based on sales volume and inventory levels.
            //    - If inventory is at or below reorder threshold => RESTOCK
            //    - Else if sales volume is below low-sales threshold => UNDERPERFORMING
            //    - Otherwise => OK
            //
            // 2. Update lastUpdated timestamp to current time.
            //
            // Note: We must not call update on this Product via EntityService; simply mutate the entity
            // and Cyoda will persist the entity as part of the workflow.

            Integer inventory = entity.getInventoryOnHand();
            Integer salesVol = entity.getTotalSalesVolume();

            // Defensive checks (entity.isValid() ensures non-null/valid numeric values),
            // but guard against unexpected nulls to be safe.
            int reorderThreshold = DEFAULT_REORDER_THRESHOLD;
            int lowSalesThreshold = DEFAULT_LOW_SALES_THRESHOLD;

            boolean needsRestock = inventory != null && inventory <= reorderThreshold;
            boolean lowSales = salesVol != null && salesVol < lowSalesThreshold;

            String previousFlag = entity.getPerformanceFlag();
            String newFlag;
            if (needsRestock) {
                newFlag = "RESTOCK";
            } else if (lowSales) {
                newFlag = "UNDERPERFORMING";
            } else {
                newFlag = "OK";
            }

            entity.setPerformanceFlag(newFlag);
            entity.setLastUpdated(Instant.now().toString());

            logger.info("Product [{}] performanceFlag updated from '{}' to '{}', inventory={}, totalSalesVolume={}",
                entity.getProductId(), previousFlag, newFlag, inventory, salesVol);

        } catch (Exception ex) {
            // Log the exception and rethrow as runtime to ensure the processor fails visibly.
            logger.error("Error while processing Product entity: {}", ex.getMessage(), ex);
            throw ex;
        }

        return entity;
    }
}