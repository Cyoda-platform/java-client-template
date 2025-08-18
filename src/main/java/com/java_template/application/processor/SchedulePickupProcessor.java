package com.java_template.application.processor;

import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
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

import java.util.UUID;

@Component
public class SchedulePickupProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SchedulePickupProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public SchedulePickupProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing SchedulePickup for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(AdoptionRequest.class)
            .validate(this::isValidEntity, "Invalid adoption request for scheduling")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(AdoptionRequest req) {
        return req != null && req.getTechnicalId() != null;
    }

    private AdoptionRequest processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<AdoptionRequest> context) {
        AdoptionRequest req = context.entity();

        if (!"APPROVED".equalsIgnoreCase(req.getStatus())) {
            logger.info("AdoptionRequest {} not APPROVED - cannot schedule (current={})", req.getTechnicalId(), req.getStatus());
            return req;
        }

        // Attempt reservation: we simulate reservation creation by generating a reservationId
        if (req.getReservationId() != null && !req.getReservationId().trim().isEmpty()) {
            logger.warn("AdoptionRequest {} already has reservation {} - idempotent schedule", req.getTechnicalId(), req.getReservationId());
            return req; // idempotent
        }

        String reservationId = "resv_" + UUID.randomUUID();
        req.setReservationId(reservationId);
        req.setStatus("SCHEDULED");
        if (req.getVersion() != null) req.setVersion(req.getVersion() + 1);

        logger.info("AdoptionRequest {} scheduled with reservation {}", req.getTechnicalId(), reservationId);
        return req;
    }
}
