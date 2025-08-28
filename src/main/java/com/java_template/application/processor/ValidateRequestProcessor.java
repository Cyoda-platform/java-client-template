package com.java_template.application.processor;

import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.application.entity.user.version_1.User;
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

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class ValidateRequestProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateRequestProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public ValidateRequestProcessor(SerializerFactory serializerFactory,
                                    EntityService entityService,
                                    ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing AdoptionRequest for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(AdoptionRequest.class)
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

    private boolean isValidEntity(AdoptionRequest entity) {
        return entity != null && entity.isValid();
    }

    private AdoptionRequest processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<AdoptionRequest> context) {
        AdoptionRequest adoptionRequest = context.entity();

        // Ensure paymentStatus has a default
        if (adoptionRequest.getPaymentStatus() == null || adoptionRequest.getPaymentStatus().isBlank()) {
            adoptionRequest.setPaymentStatus("NOT_PAID");
        }

        // Basic sanity checks for referenced IDs
        if (adoptionRequest.getPetId() == null || adoptionRequest.getPetId().isBlank()) {
            adoptionRequest.setStatus("REJECTED");
            adoptionRequest.setNotes("PetId is missing");
            logger.warn("AdoptionRequest {} rejected: missing petId", adoptionRequest.getRequestId());
            return adoptionRequest;
        }
        if (adoptionRequest.getUserId() == null || adoptionRequest.getUserId().isBlank()) {
            adoptionRequest.setStatus("REJECTED");
            adoptionRequest.setNotes("UserId is missing");
            logger.warn("AdoptionRequest {} rejected: missing userId", adoptionRequest.getRequestId());
            return adoptionRequest;
        }

        // Attempt to load referenced Pet and User entities by their technical UUIDs.
        Pet pet = null;
        User user = null;
        try {
            UUID petUuid = UUID.fromString(adoptionRequest.getPetId());
            CompletableFuture<DataPayload> petFuture = entityService.getItem(petUuid);
            DataPayload petPayload = petFuture != null ? petFuture.get() : null;
            if (petPayload == null || petPayload.getData() == null) {
                adoptionRequest.setStatus("REJECTED");
                adoptionRequest.setNotes("Referenced pet not found");
                logger.warn("AdoptionRequest {} rejected: pet not found ({})", adoptionRequest.getRequestId(), adoptionRequest.getPetId());
                return adoptionRequest;
            }
            pet = objectMapper.treeToValue(petPayload.getData(), Pet.class);
        } catch (IllegalArgumentException iae) {
            adoptionRequest.setStatus("REJECTED");
            adoptionRequest.setNotes("Invalid petId format");
            logger.warn("AdoptionRequest {} rejected: invalid petId format: {}", adoptionRequest.getRequestId(), iae.getMessage());
            return adoptionRequest;
        } catch (InterruptedException | ExecutionException ex) {
            logger.error("Error fetching pet for adoptionRequest {}: {}", adoptionRequest.getRequestId(), ex.getMessage(), ex);
            adoptionRequest.setStatus("REJECTED");
            adoptionRequest.setNotes("Error fetching pet information");
            return adoptionRequest;
        } catch (Exception ex) {
            logger.error("Unexpected error converting pet payload: {}", ex.getMessage(), ex);
            adoptionRequest.setStatus("REJECTED");
            adoptionRequest.setNotes("Error processing pet information");
            return adoptionRequest;
        }

        try {
            UUID userUuid = UUID.fromString(adoptionRequest.getUserId());
            CompletableFuture<DataPayload> userFuture = entityService.getItem(userUuid);
            DataPayload userPayload = userFuture != null ? userFuture.get() : null;
            if (userPayload == null || userPayload.getData() == null) {
                adoptionRequest.setStatus("REJECTED");
                adoptionRequest.setNotes("Referenced user not found");
                logger.warn("AdoptionRequest {} rejected: user not found ({})", adoptionRequest.getRequestId(), adoptionRequest.getUserId());
                return adoptionRequest;
            }
            user = objectMapper.treeToValue(userPayload.getData(), User.class);
        } catch (IllegalArgumentException iae) {
            adoptionRequest.setStatus("REJECTED");
            adoptionRequest.setNotes("Invalid userId format");
            logger.warn("AdoptionRequest {} rejected: invalid userId format: {}", adoptionRequest.getRequestId(), iae.getMessage());
            return adoptionRequest;
        } catch (InterruptedException | ExecutionException ex) {
            logger.error("Error fetching user for adoptionRequest {}: {}", adoptionRequest.getRequestId(), ex.getMessage(), ex);
            adoptionRequest.setStatus("REJECTED");
            adoptionRequest.setNotes("Error fetching user information");
            return adoptionRequest;
        } catch (Exception ex) {
            logger.error("Unexpected error converting user payload: {}", ex.getMessage(), ex);
            adoptionRequest.setStatus("REJECTED");
            adoptionRequest.setNotes("Error processing user information");
            return adoptionRequest;
        }

        // Business rule: Pet must be Available
        if (pet.getStatus() == null || !pet.getStatus().equalsIgnoreCase("Available")) {
            adoptionRequest.setStatus("REJECTED");
            adoptionRequest.setNotes("Pet is not available for adoption");
            logger.info("AdoptionRequest {} rejected: pet {} not available (status={})", adoptionRequest.getRequestId(), adoptionRequest.getPetId(), pet.getStatus());
            return adoptionRequest;
        }

        // Business rule: User must be eligible (Active or Trusted)
        String userStatus = user.getStatus();
        if (userStatus == null ||
            !(userStatus.equalsIgnoreCase("Active") || userStatus.equalsIgnoreCase("Trusted"))) {
            adoptionRequest.setStatus("REJECTED");
            adoptionRequest.setNotes("User is not eligible to request adoption");
            logger.info("AdoptionRequest {} rejected: user {} not eligible (status={})", adoptionRequest.getRequestId(), adoptionRequest.getUserId(), userStatus);
            return adoptionRequest;
        }

        // All validations passed -> mark for review
        adoptionRequest.setStatus("PENDING_REVIEW");
        if (adoptionRequest.getNotes() == null || adoptionRequest.getNotes().isBlank()) {
            adoptionRequest.setNotes("Validated and assigned for review");
        }
        logger.info("AdoptionRequest {} validated successfully and moved to PENDING_REVIEW", adoptionRequest.getRequestId());

        return adoptionRequest;
    }
}