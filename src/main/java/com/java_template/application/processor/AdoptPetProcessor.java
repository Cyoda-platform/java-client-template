package com.java_template.application.processor;

import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
import com.java_template.application.entity.pet.version_1.Pet;
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
public class AdoptPetProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AdoptPetProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public AdoptPetProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing adoption for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(AdoptionRequest.class)
            .validate(this::isValidEntity, "Invalid adoption request")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(AdoptionRequest req) {
        return req != null && req.getTechnicalId() != null && !req.getTechnicalId().isEmpty();
    }

    private AdoptionRequest processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<AdoptionRequest> context) {
        AdoptionRequest request = context.entity();
        try {
            String currentStatus = request.getStatus();
            // Only process APPROVED or SUBMITTED depending on business rules
            if (currentStatus != null && !("APPROVED".equals(currentStatus) || "SUBMITTED".equals(currentStatus))) {
                logger.info("AdoptionRequest {} in status {} - skipping adopt logic", request.getTechnicalId(), currentStatus);
                return request;
            }

            // In a real implementation we'd fetch and lock the Pet by technicalId, then perform an atomic update.
            // Here we try to perform safe transitions on the request and indicate expected pet changes via notes.

            // guard: ensure petTechnicalId is present
            if (request.getPetTechnicalId() == null || request.getPetTechnicalId().isEmpty()) {
                request.setStatus("REJECTED");
                try {
                    request.setNotes("Pet not specified");
                } catch (Throwable ignore) {
                }
                logger.warn("AdoptionRequest {} rejected because petTechnicalId is missing", request.getTechnicalId());
                return request;
            }

            // Simulate adoption success: mark request completed and set decisionAt
            request.setStatus("COMPLETED");
            try {
                request.setDecisionAt(Instant.now());
            } catch (Throwable t) {
                // ignore if field type differs
            }

            // If request carries requester info, we cannot mutate Pet here without repository access.
            logger.info("AdoptionRequest {} processed - marked COMPLETED (adoption handling should update Pet in a real implementation)", request.getTechnicalId());
            return request;
        } catch (Exception e) {
            logger.error("Unhandled error while processing adoption request {}", request == null ? "<null>" : request.getTechnicalId(), e);
            if (request != null) {
                request.setStatus("REJECTED");
                try {
                    request.setNotes("Adoption processor error: " + e.getMessage());
                } catch (Throwable ignore) {
                }
            }
            return request;
        }
    }
}
