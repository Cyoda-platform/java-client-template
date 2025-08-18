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

@Component
public class ConfirmPickupProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ConfirmPickupProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ConfirmPickupProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ConfirmPickup for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(AdoptionRequest.class)
            .validate(this::isValidEntity, "Invalid adoption request for confirm pickup")
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

        if (!"SCHEDULED".equalsIgnoreCase(req.getStatus())) {
            logger.info("AdoptionRequest {} not in SCHEDULED state - cannot confirm pickup (current={})", req.getTechnicalId(), req.getStatus());
            return req;
        }

        // In a real implementation we'd validate reservation belongs to this request; we assume it does when present
        if (req.getReservationId() == null || req.getReservationId().trim().isEmpty()) {
            req.setStatus("REJECTED");
            req.setReviewNotes("No valid reservation present");
            logger.warn("AdoptionRequest {} has no reservation - marking REJECTED", req.getTechnicalId());
            if (req.getVersion() != null) req.setVersion(req.getVersion() + 1);
            return req;
        }

        // Mark request completed; pet update handled by downstream FinalizeAdoptionProcessor or similar
        req.setStatus("COMPLETED");
        if (req.getVersion() != null) req.setVersion(req.getVersion() + 1);
        logger.info("AdoptionRequest {} marked COMPLETED on pickup", req.getTechnicalId());
        return req;
    }
}
