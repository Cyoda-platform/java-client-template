package com.java_template.application.processor;

import com.java_template.application.entity.InventoryMetrics;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.config.Config;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class InventoryMetricsProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public InventoryMetricsProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("InventoryMetricsProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing InventoryMetrics for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(InventoryMetrics.class)
                .withErrorHandler(this::handleInventoryMetricsError)
                .validate(InventoryMetrics::isValid, "Invalid InventoryMetrics state")
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "InventoryMetricsProcessor".equals(modelSpec.operationName()) &&
                "inventoryMetrics".equals(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private ErrorInfo handleInventoryMetricsError(Throwable throwable, InventoryMetrics metrics) {
        logger.error("Error processing InventoryMetrics", throwable);
        return new ErrorInfo("InventoryMetricsProcessingError", throwable.getMessage());
    }
}
