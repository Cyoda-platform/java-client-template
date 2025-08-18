package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ValidateRequestProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateRequestProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public ValidateRequestProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing AdoptionRequest validation for request: {}", request.getId());

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
        return req != null && req.getId() != null && !req.getId().trim().isEmpty();
    }

    private AdoptionRequest processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<AdoptionRequest> context) {
        AdoptionRequest req = context.entity();

        String status = req.getStatus();
        if (status == null || !"SUBMITTED".equalsIgnoreCase(status)) {
            logger.info("AdoptionRequest {} not in SUBMITTED state (current={}) - skipping validation", req.getId(), status);
            return req;
        }

        // Basic validations: required ids present
        if (req.getPetId() == null || req.getPetId().trim().isEmpty()) {
            req.setStatus("REJECTED");
            req.setReviewNotes("Missing petId");
            logger.warn("AdoptionRequest {} rejected - missing petId", req.getId());
            return req;
        }
        if (req.getUserId() == null || req.getUserId().trim().isEmpty()) {
            req.setStatus("REJECTED");
            req.setReviewNotes("Missing userId");
            logger.warn("AdoptionRequest {} rejected - missing userId", req.getId());
            return req;
        }

        // Load related entities to perform eligibility checks
        try {
            UUID petUuid = UUID.fromString(req.getPetId());
            UUID userUuid = UUID.fromString(req.getUserId());

            ObjectNode petNode = entityService.getItem(
                com.java_template.application.entity.pet.version_1.Pet.ENTITY_NAME,
                String.valueOf(com.java_template.application.entity.pet.version_1.Pet.ENTITY_VERSION),
                petUuid
            ).join();

            if (petNode == null) {
                req.setStatus("REJECTED");
                req.setReviewNotes("Pet not found");
                logger.warn("AdoptionRequest {} rejected - pet {} not found", req.getId(), req.getPetId());
                return req;
            }

            String petStatus = null;
            JsonNode statusNode = petNode.get("status");
            if (statusNode != null && !statusNode.isNull()) petStatus = statusNode.asText();

            if (petStatus == null || !"AVAILABLE".equalsIgnoreCase(petStatus)) {
                req.setStatus("REJECTED");
                req.setReviewNotes("Pet not available");
                logger.warn("AdoptionRequest {} rejected - pet {} not available (status={})", req.getId(), req.getPetId(), petStatus);
                return req;
            }

            ObjectNode userNode = entityService.getItem(
                com.java_template.application.entity.user.version_1.User.ENTITY_NAME,
                String.valueOf(com.java_template.application.entity.user.version_1.User.ENTITY_VERSION),
                userUuid
            ).join();

            if (userNode == null) {
                req.setStatus("REJECTED");
                req.setReviewNotes("User not found");
                logger.warn("AdoptionRequest {} rejected - user {} not found", req.getId(), req.getUserId());
                return req;
            }

            // Basic user eligibility: must have contact email
            JsonNode emailNode = userNode.get("contactEmail");
            if (emailNode == null || emailNode.asText().isBlank()) {
                req.setStatus("REJECTED");
                req.setReviewNotes("User contact email missing");
                logger.warn("AdoptionRequest {} rejected - user {} missing contact email", req.getId(), req.getUserId());
                return req;
            }

            // Passed basic validations
            req.setStatus("UNDER_REVIEW");
            logger.info("AdoptionRequest {} moved to UNDER_REVIEW", req.getId());

        } catch (IllegalArgumentException iae) {
            // UUID parsing issues
            req.setStatus("REJECTED");
            req.setReviewNotes("Invalid id format");
            logger.warn("AdoptionRequest {} rejected - invalid id format: {}", req.getId(), iae.getMessage());
        } catch (Exception e) {
            // transient error - keep request in SUBMITTED so it can be retried
            logger.error("Error validating AdoptionRequest {}: {}", req.getId(), e.getMessage(), e);
        }

        return req;
    }
}
