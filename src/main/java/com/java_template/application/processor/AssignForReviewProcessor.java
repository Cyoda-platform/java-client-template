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

import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class AssignForReviewProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AssignForReviewProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public AssignForReviewProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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
        AdoptionRequest entity = context.entity();

        try {
            // Default safety: ensure a paymentStatus exists
            if (entity.getPaymentStatus() == null || entity.getPaymentStatus().isBlank()) {
                entity.setPaymentStatus("NOT_PAID");
            }

            // If request already moved past review-related states, do nothing
            String currentStatus = entity.getStatus();
            if (currentStatus != null) {
                String s = currentStatus.trim().toUpperCase();
                if (s.equals("PENDING_REVIEW") || s.equals("APPROVED") || s.equals("REJECTED") || s.equals("COMPLETED") || s.equals("CLOSED")) {
                    // already processed for review or beyond - no changes
                    logger.info("AdoptionRequest {} in state {} - skipping AssignForReview logic", entity.getRequestId(), currentStatus);
                    return entity;
                }
            }

            // 1. Validate Pet existence and availability
            List<DataPayload> petPayloads = null;
            if (entity.getPetId() != null && !entity.getPetId().isBlank()) {
                SearchConditionRequest petCondition = SearchConditionRequest.group("AND",
                    Condition.of("$.petId", "EQUALS", entity.getPetId())
                );
                CompletableFuture<List<DataPayload>> petFuture = entityService.getItemsByCondition(
                    Pet.ENTITY_NAME,
                    Pet.ENTITY_VERSION,
                    petCondition,
                    true
                );
                petPayloads = petFuture.get();
            }

            Pet foundPet = null;
            if (petPayloads != null && !petPayloads.isEmpty()) {
                DataPayload pld = petPayloads.get(0);
                foundPet = objectMapper.treeToValue(pld.getData(), Pet.class);
            }

            if (foundPet == null) {
                entity.setStatus("REJECTED");
                String note = "Pet not found for petId: " + entity.getPetId();
                entity.setNotes(concatNotes(entity.getNotes(), note));
                logger.warn(note + " - rejecting request {}", entity.getRequestId());
                return entity;
            }

            if (foundPet.getStatus() == null || !foundPet.getStatus().equalsIgnoreCase("Available")) {
                entity.setStatus("REJECTED");
                String note = "Pet not available for adoption (status=" + foundPet.getStatus() + ")";
                entity.setNotes(concatNotes(entity.getNotes(), note));
                logger.warn(note + " - rejecting request {}", entity.getRequestId());
                return entity;
            }

            // 2. Validate User existence and eligibility
            List<DataPayload> userPayloads = null;
            if (entity.getUserId() != null && !entity.getUserId().isBlank()) {
                SearchConditionRequest userCondition = SearchConditionRequest.group("AND",
                    Condition.of("$.userId", "EQUALS", entity.getUserId())
                );
                CompletableFuture<List<DataPayload>> userFuture = entityService.getItemsByCondition(
                    User.ENTITY_NAME,
                    User.ENTITY_VERSION,
                    userCondition,
                    true
                );
                userPayloads = userFuture.get();
            }

            User foundUser = null;
            if (userPayloads != null && !userPayloads.isEmpty()) {
                DataPayload upld = userPayloads.get(0);
                foundUser = objectMapper.treeToValue(upld.getData(), User.class);
            }

            if (foundUser == null) {
                entity.setStatus("REJECTED");
                String note = "User not found for userId: " + entity.getUserId();
                entity.setNotes(concatNotes(entity.getNotes(), note));
                logger.warn(note + " - rejecting request {}", entity.getRequestId());
                return entity;
            }

            String userStatus = foundUser.getStatus() != null ? foundUser.getStatus().trim().toUpperCase() : "";
            if (userStatus.equals("SUSPENDED")) {
                entity.setStatus("REJECTED");
                String note = "User is suspended and not eligible for adoption";
                entity.setNotes(concatNotes(entity.getNotes(), note));
                logger.warn("{} - rejecting request {}", note, entity.getRequestId());
                return entity;
            }

            // Passed basic checks -> assign for human review
            entity.setStatus("PENDING_REVIEW");
            String reviewNote = "Assigned for review. Home visit required: " + Boolean.toString(Boolean.TRUE.equals(entity.getHomeVisitRequired()));
            entity.setNotes(concatNotes(entity.getNotes(), reviewNote));
            logger.info("AdoptionRequest {} moved to PENDING_REVIEW", entity.getRequestId());

            return entity;

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while processing AssignForReviewProcessor for request {}: {}", entity.getRequestId(), ie.getMessage(), ie);
            entity.setStatus("REJECTED");
            entity.setNotes(concatNotes(entity.getNotes(), "Processing interrupted"));
            return entity;
        } catch (ExecutionException ee) {
            logger.error("Execution error while processing AssignForReviewProcessor for request {}: {}", entity.getRequestId(), ee.getMessage(), ee);
            entity.setStatus("REJECTED");
            entity.setNotes(concatNotes(entity.getNotes(), "Processing error: " + ee.getMessage()));
            return entity;
        } catch (Exception ex) {
            logger.error("Unexpected error in AssignForReviewProcessor for request {}: {}", entity.getRequestId(), ex.getMessage(), ex);
            entity.setStatus("REJECTED");
            entity.setNotes(concatNotes(entity.getNotes(), "Unexpected error: " + ex.getMessage()));
            return entity;
        }
    }

    private String concatNotes(String existing, String addition) {
        if (existing == null || existing.isBlank()) return addition;
        return existing + " | " + addition;
    }
}