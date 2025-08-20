package com.java_template.application.processor;

import com.java_template.application.entity.shipment.version_1.Shipment;
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
public class CarrierUpdateProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CarrierUpdateProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CarrierUpdateProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Shipment carrier update for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Shipment.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Shipment s) {
        return s != null && s.getStatus() != null;
    }

    private Shipment processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Shipment> context) {
        Shipment s = context.entity();
        if (s == null) return null;

        if ("IN_TRANSIT".equalsIgnoreCase(s.getStatus()) || "DELIVERED".equalsIgnoreCase(s.getStatus())) {
            logger.info("Shipment already in transit or delivered: {}", s.getShipmentId());
            return s;
        }

        // simulate carrier update progression
        if ("SHIPPED".equalsIgnoreCase(s.getStatus())) {
            s.setStatus("IN_TRANSIT");
            try {
                java.util.Map<String,Object> meta = s.getMetadata() == null || !(s.getMetadata() instanceof java.util.Map) ? new java.util.HashMap<>() : (java.util.Map<String,Object>) s.getMetadata();
                meta.put("inTransitAt", java.time.Instant.now().toString());
                s.setMetadata(meta);
            } catch (Exception ignored) {}
            logger.info("Shipment {} moved to IN_TRANSIT", s.getShipmentId());
        }

        return s;
    }
}
