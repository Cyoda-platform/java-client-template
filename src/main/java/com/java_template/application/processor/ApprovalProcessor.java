package com.java_template.application.processor;

import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.application.entity.owner.version_1.Owner;
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

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class ApprovalProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ApprovalProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public ApprovalProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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

        // Business logic:
        // - Check that request is under review before approving.
        // - Validate Eligibility: requester (Owner) must be verified, pet must be AVAILABLE.
        // - If eligible -> set status = "approved", set reviewerId if missing, set decisionAt timestamp.
        // - If not eligible -> set status = "rejected", set decisionAt timestamp and add note if appropriate.
        // Note: Do NOT modify the Pet/Owner entities here beyond reads; completion of adoption is handled by CompleteAdoptionProcessor.

        try {
            String currentStatus = entity.getStatus();
            if (currentStatus == null) {
                logger.warn("AdoptionRequest {} has null status; rejecting", entity.getRequestId());
                entity.setStatus("rejected");
                entity.setDecisionAt(Instant.now().toString());
                if (entity.getReviewerId() == null || entity.getReviewerId().isBlank()) {
                    entity.setReviewerId("system");
                }
                return entity;
            }

            if (!"under_review".equalsIgnoreCase(currentStatus) && !"submitted".equalsIgnoreCase(currentStatus)) {
                // Only allow approval flow for requests that are under_review (or submitted -> treated as under review)
                logger.info("AdoptionRequest {} is in status '{}' and not eligible for approval; skipping state change", entity.getRequestId(), currentStatus);
                return entity;
            }

            // Fetch Pet by petId (match external id field 'id' on Pet)
            Pet pet = null;
            if (entity.getPetId() != null && !entity.getPetId().isBlank()) {
                SearchConditionRequest petCondition = SearchConditionRequest.group("AND",
                    Condition.of("$.id", "EQUALS", entity.getPetId())
                );
                CompletableFuture<List<DataPayload>> petFuture = entityService.getItemsByCondition(
                    Pet.ENTITY_NAME,
                    Pet.ENTITY_VERSION,
                    petCondition,
                    true
                );
                List<DataPayload> petPayloads = petFuture.get();
                if (petPayloads != null && !petPayloads.isEmpty()) {
                    DataPayload payload = petPayloads.get(0);
                    if (payload != null && payload.getData() != null) {
                        pet = objectMapper.treeToValue(payload.getData(), Pet.class);
                    }
                }
            }

            // Fetch Owner by requesterId (match ownerId on Owner)
            Owner owner = null;
            if (entity.getRequesterId() != null && !entity.getRequesterId().isBlank()) {
                SearchConditionRequest ownerCondition = SearchConditionRequest.group("AND",
                    Condition.of("$.ownerId", "EQUALS", entity.getRequesterId())
                );
                CompletableFuture<List<DataPayload>> ownerFuture = entityService.getItemsByCondition(
                    Owner.ENTITY_NAME,
                    Owner.ENTITY_VERSION,
                    ownerCondition,
                    true
                );
                List<DataPayload> ownerPayloads = ownerFuture.get();
                if (ownerPayloads != null && !ownerPayloads.isEmpty()) {
                    DataPayload payload = ownerPayloads.get(0);
                    if (payload != null && payload.getData() != null) {
                        owner = objectMapper.treeToValue(payload.getData(), Owner.class);
                    }
                }
            }

            boolean petAvailable = pet != null && pet.getStatus() != null && "available".equalsIgnoreCase(pet.getStatus());
            boolean ownerVerified = owner != null && owner.getVerificationStatus() != null && "verified".equalsIgnoreCase(owner.getVerificationStatus());

            if (petAvailable && ownerVerified) {
                entity.setStatus("approved");
                if (entity.getReviewerId() == null || entity.getReviewerId().isBlank()) {
                    // If no explicit reviewer provided, mark as system reviewer for bookkeeping
                    entity.setReviewerId("system");
                }
                entity.setDecisionAt(Instant.now().toString());
                logger.info("AdoptionRequest {} approved for pet {} by requester {}", entity.getRequestId(), entity.getPetId(), entity.getRequesterId());
            } else {
                // Not eligible for approval
                entity.setStatus("rejected");
                if (entity.getReviewerId() == null || entity.getReviewerId().isBlank()) {
                    entity.setReviewerId("system");
                }
                entity.setDecisionAt(Instant.now().toString());
                StringBuilder notes = new StringBuilder();
                if (!petAvailable) {
                    notes.append("Pet not available; ");
                }
                if (!ownerVerified) {
                    notes.append("Requester not verified; ");
                }
                String existingNotes = entity.getNotes();
                String combined = (existingNotes == null ? "" : existingNotes + " ") + notes.toString().trim();
                entity.setNotes(combined.isBlank() ? null : combined);
                logger.info("AdoptionRequest {} rejected: {}", entity.getRequestId(), notes.toString().trim());
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while checking eligibility for AdoptionRequest {}: {}", entity.getRequestId(), e.getMessage(), e);
            entity.setStatus("rejected");
            entity.setDecisionAt(Instant.now().toString());
            if (entity.getReviewerId() == null || entity.getReviewerId().isBlank()) {
                entity.setReviewerId("system");
            }
            String existingNotes = entity.getNotes();
            entity.setNotes((existingNotes == null ? "" : existingNotes + " ") + "Approval interrupted");
        } catch (ExecutionException e) {
            logger.error("Execution error while checking eligibility for AdoptionRequest {}: {}", entity.getRequestId(), e.getMessage(), e);
            entity.setStatus("rejected");
            entity.setDecisionAt(Instant.now().toString());
            if (entity.getReviewerId() == null || entity.getReviewerId().isBlank()) {
                entity.setReviewerId("system");
            }
            String existingNotes = entity.getNotes();
            entity.setNotes((existingNotes == null ? "" : existingNotes + " ") + "Error during eligibility checks: " + e.getCause());
        } catch (Exception e) {
            logger.error("Unexpected error in ApprovalProcessor for AdoptionRequest {}: {}", entity.getRequestId(), e.getMessage(), e);
            entity.setStatus("rejected");
            entity.setDecisionAt(Instant.now().toString());
            if (entity.getReviewerId() == null || entity.getReviewerId().isBlank()) {
                entity.setReviewerId("system");
            }
            String existingNotes = entity.getNotes();
            entity.setNotes((existingNotes == null ? "" : existingNotes + " ") + "Unexpected error: " + e.getMessage());
        }

        return entity;
    }
}