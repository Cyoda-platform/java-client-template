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
public class FinalizeAdoptionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FinalizeAdoptionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public FinalizeAdoptionProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing FinalizeAdoption for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(AdoptionRequest.class)
            .validate(this::isValidEntity, "Invalid adoption request for finalize adoption")
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

        // Only finalize if request is COMPLETED
        if (!"COMPLETED".equalsIgnoreCase(req.getStatus())) {
            logger.info("AdoptionRequest {} not COMPLETED - skipping finalization (current={})", req.getId(), req.getStatus());
            return req;
        }

        // Validate pet exists and clear reservation/mark adopted
        if (req.getPetId() == null || req.getPetId().trim().isEmpty()) {
            logger.warn("AdoptionRequest {} has no petId - cannot finalize adoption", req.getId());
            return req;
        }

        try {
            UUID petUuid = UUID.fromString(req.getPetId());
            ObjectNode petNode = entityService.getItem(Pet.ENTITY_NAME, String.valueOf(Pet.ENTITY_VERSION), petUuid).join();
            if (petNode == null) {
                logger.warn("AdoptionRequest {} pet {} not found - cannot finalize", req.getId(), req.getPetId());
                return req;
            }

            // Verify reservation - we stored reservation token in reviewNotes earlier
            String reservationId = null;
            if (req.getReviewNotes() != null && req.getReviewNotes().contains("reservation:")) {
                int idx = req.getReviewNotes().indexOf("reservation:");
                reservationId = req.getReviewNotes().substring(idx + "reservation:".length());
            }

            // For robustness, proceed even if reservation not present but log
            if (reservationId == null) {
                logger.warn("AdoptionRequest {} has no reservation token - proceeding to mark pet as ADOPTED if allowed", req.getId());
            }

            // Mark pet as ADOPTED
            petNode.put("status", "ADOPTED");
            petNode.put("updatedAt", java.time.OffsetDateTime.now().toString());
            entityService.updateItem(Pet.ENTITY_NAME, String.valueOf(Pet.ENTITY_VERSION), petUuid, petNode).join();

            // Mark request CLOSED
            req.setStatus("CLOSED");
            req.setReviewNotes((req.getReviewNotes() == null ? "" : req.getReviewNotes() + " | ") + "finalizedAt:" + java.time.OffsetDateTime.now().toString());
            logger.info("AdoptionRequest {} finalized - pet {} marked ADOPTED", req.getId(), req.getPetId());

        } catch (IllegalArgumentException iae) {
            logger.warn("AdoptionRequest {} has invalid petId {} - cannot finalize", req.getId(), req.getPetId());
        } catch (Exception e) {
            logger.error("Error finalizing adoption for request {}: {}", req.getId(), e.getMessage(), e);
        }

        return req;
    }
}
