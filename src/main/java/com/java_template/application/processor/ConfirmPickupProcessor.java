package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
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
public class ConfirmPickupProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ConfirmPickupProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public ConfirmPickupProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
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
        return req != null && req.getId() != null && !req.getId().trim().isEmpty();
    }

    private AdoptionRequest processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<AdoptionRequest> context) {
        AdoptionRequest req = context.entity();

        if (!"SCHEDULED".equalsIgnoreCase(req.getStatus())) {
            logger.info("AdoptionRequest {} not in SCHEDULED state - cannot confirm pickup (current={})", req.getId(), req.getStatus());
            return req;
        }

        // Validate reservation by checking pet's status/reservation notes
        try {
            if (req.getPetId() == null || req.getPetId().trim().isEmpty()) {
                req.setStatus("REJECTED");
                req.setReviewNotes("Missing petId during pickup confirmation");
                logger.warn("AdoptionRequest {} missing petId during confirm pickup", req.getId());
                return req;
            }

            UUID petUuid = UUID.fromString(req.getPetId());
            ObjectNode petNode = entityService.getItem(Pet.ENTITY_NAME, String.valueOf(Pet.ENTITY_VERSION), petUuid).join();
            if (petNode == null) {
                req.setStatus("REJECTED");
                req.setReviewNotes("Pet not found during pickup confirmation");
                logger.warn("AdoptionRequest {} pet {} not found during confirm pickup", req.getId(), req.getPetId());
                return req;
            }

            String petStatus = null;
            if (petNode.get("status") != null && !petNode.get("status").isNull()) petStatus = petNode.get("status").asText();

            if (!"RESERVED".equalsIgnoreCase(petStatus)) {
                req.setStatus("REJECTED");
                req.setReviewNotes("Pet not reserved at pickup time");
                logger.warn("AdoptionRequest {} confirm pickup failed - pet {} status={} not RESERVED", req.getId(), req.getPetId(), petStatus);
                return req;
            }

            // All good - mark request COMPLETED; finalization will mark pet ADOPTED
            req.setStatus("COMPLETED");
            req.setReviewNotes((req.getReviewNotes() == null ? "" : req.getReviewNotes() + " | ") + "pickedUpAt:" + java.time.OffsetDateTime.now().toString());
            logger.info("AdoptionRequest {} pickup confirmed - moved to COMPLETED", req.getId());

        } catch (IllegalArgumentException iae) {
            req.setStatus("REJECTED");
            req.setReviewNotes("Invalid petId format during pickup confirmation");
            logger.warn("AdoptionRequest {} confirm pickup failed - invalid petId {}", req.getId(), req.getPetId());
        } catch (Exception e) {
            logger.error("Error during confirm pickup for AdoptionRequest {}: {}", req.getId(), e.getMessage(), e);
        }

        if (req.getStatus() != null && req.getStatus().equalsIgnoreCase("COMPLETED") && req.getId() != null) {
            // bump version to indicate state change in this processor
            if (req.getStatus() != null) {
                // version bump handled outside if model includes a version field; our model does not include version so we skip
            }
        }

        return req;
    }
}
