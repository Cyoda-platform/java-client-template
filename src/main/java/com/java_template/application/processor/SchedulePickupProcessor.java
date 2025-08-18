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
public class SchedulePickupProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SchedulePickupProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public SchedulePickupProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
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
        return req != null && req.getId() != null && !req.getId().trim().isEmpty();
    }

    private AdoptionRequest processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<AdoptionRequest> context) {
        AdoptionRequest req = context.entity();

        if (!"APPROVED".equalsIgnoreCase(req.getStatus())) {
            logger.info("AdoptionRequest {} not APPROVED - cannot schedule (current={})", req.getId(), req.getStatus());
            return req;
        }

        // Ensure petId exists
        if (req.getPetId() == null || req.getPetId().trim().isEmpty()) {
            req.setStatus("REJECTED");
            req.setReviewNotes("Missing petId for scheduling");
            logger.warn("AdoptionRequest {} missing petId - rejecting schedule", req.getId());
            return req;
        }

        try {
            UUID petUuid = UUID.fromString(req.getPetId());
            ObjectNode petNode = entityService.getItem(Pet.ENTITY_NAME, String.valueOf(Pet.ENTITY_VERSION), petUuid).join();
            if (petNode == null) {
                req.setStatus("REJECTED");
                req.setReviewNotes("Pet not found during scheduling");
                logger.warn("AdoptionRequest {} scheduling failed - pet {} not found", req.getId(), req.getPetId());
                return req;
            }

            String petStatus = null;
            if (petNode.get("status") != null && !petNode.get("status").isNull()) petStatus = petNode.get("status").asText();

            if (petStatus == null || !"AVAILABLE".equalsIgnoreCase(petStatus)) {
                req.setStatus("REJECTED");
                req.setReviewNotes("Pet not available for reservation");
                logger.warn("AdoptionRequest {} scheduling failed - pet {} status={} is not available", req.getId(), req.getPetId(), petStatus);
                return req;
            }

            // Attempt to set pet status to RESERVED atomically via entityService.updateItem
            petNode.put("status", "RESERVED");
            // update timestamps if present
            petNode.put("updatedAt", java.time.OffsetDateTime.now().toString());

            entityService.updateItem(Pet.ENTITY_NAME, String.valueOf(Pet.ENTITY_VERSION), petUuid, petNode).join();

            // Set scheduled state on request. Reservation id not modelled on AdoptionRequest entity, so we store a reservation token in reviewNotes for traceability
            String reservationId = "resv_" + UUID.randomUUID();
            String notes = (req.getReviewNotes() == null ? "" : req.getReviewNotes() + " | ") + "reservation:" + reservationId;
            req.setReviewNotes(notes);
            req.setStatus("SCHEDULED");

            logger.info("AdoptionRequest {} scheduled and pet {} reserved (reservation={})", req.getId(), req.getPetId(), reservationId);

        } catch (IllegalArgumentException iae) {
            req.setStatus("REJECTED");
            req.setReviewNotes("Invalid petId format");
            logger.warn("AdoptionRequest {} scheduling failed - invalid petId {}", req.getId(), req.getPetId());
        } catch (Exception e) {
            logger.error("Error scheduling AdoptionRequest {}: {}", req.getId(), e.getMessage(), e);
            // transient error - keep in APPROVED so it can be retried
        }

        return req;
    }
}
