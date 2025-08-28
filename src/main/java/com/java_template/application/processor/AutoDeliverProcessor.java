package com.java_template.application.processor;
import com.java_template.application.entity.shipment.version_1.Shipment;
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

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
public class AutoDeliverProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AutoDeliverProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public AutoDeliverProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Shipment for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Shipment.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }
    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Shipment entity) {
        return entity != null && entity.isValid();
    }

    private Shipment processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Shipment> context) {
        Shipment entity = context.entity();

        try {
            // Only auto-deliver shipments that are currently SENT
            String status = entity.getStatus();
            if (status == null) {
                logger.warn("Shipment {} has null status, skipping auto-deliver", entity.getId());
                return entity;
            }

            if (!"SENT".equalsIgnoreCase(status)) {
                logger.info("Shipment {} status is '{}', not eligible for auto-deliver", entity.getId(), status);
                return entity;
            }

            // Wait 5 seconds before moving to DELIVERED as per spec
            logger.info("Auto-deliver: waiting 5s before marking shipment {} as DELIVERED", entity.getId());
            try {
                Thread.sleep(5000L);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                logger.warn("Auto-deliver sleep interrupted for shipment {}: {}", entity.getId(), ie.getMessage());
            }

            // Update shipment status to DELIVERED
            entity.setStatus("DELIVERED");
            // Add delivered timestamp to trackingInfo map, preserving existing entries
            Map<String, Object> tracking = entity.getTrackingInfo();
            if (tracking == null) {
                tracking = new HashMap<>();
            }
            tracking.put("deliveredAt", OffsetDateTime.now().toString());
            entity.setTrackingInfo(tracking);

            logger.info("Shipment {} marked as DELIVERED", entity.getId());
        } catch (Exception ex) {
            logger.error("Error while processing auto-deliver for shipment {}: {}", entity != null ? entity.getId() : "null", ex.getMessage(), ex);
            // Do not throw; return entity as-is so workflow can handle error surface if needed
        }

        return entity;
    }
}