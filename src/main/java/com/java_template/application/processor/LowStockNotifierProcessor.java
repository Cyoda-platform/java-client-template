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

@Component
public class LowStockNotifierProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(LowStockNotifierProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public LowStockNotifierProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing LowStockNotifier for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Product.class)
            .validate(this::isValidEntity, "Invalid product for low stock notification")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Product product) {
        return product != null && product.isValid();
    }

    private ProcessorSerializer.ProcessorEntityExecutionContext<Product> processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Product> context) {
        Product product = context.entity();
        // In a real system we'd send emails/notifications. For prototype we log.
        logger.warn("Low stock detected for product {}: stock={} reserved={}", product.getId(), product.getStockQuantity(), getReserved(product));
        return context;
    }

    private int getReserved(Product p) {
        try {
            java.lang.reflect.Field f = Product.class.getDeclaredField("reservedQuantity");
            f.setAccessible(true);
            Object val = f.get(p);
            if (val instanceof Integer) return (Integer) val;
        } catch (Exception ignored) {}
        return 0;
    }
}
