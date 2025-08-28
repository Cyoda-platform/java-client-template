package com.java_template.application.processor;

import static com.java_template.common.config.Config.*;

import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
import com.java_template.application.entity.owner.version_1.Owner;
import com.java_template.application.entity.pet.version_1.Pet;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;

import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

@Component
public class QueueForReviewProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(QueueForReviewProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public QueueForReviewProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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
        // - Check eligibility: requester (Owner) must be verified and Pet must be AVAILABLE
        // - If not eligible -> mark request.status = "rejected" and set decisionAt timestamp
        // - If eligible -> set request.status = "under_review" (queued for staff)
        // Note: We only modify the current entity's fields. Any reads of other entities use EntityService.

        String requesterId = entity.getRequesterId();
        String petRef = entity.getPetId();

        boolean eligible = true;
        String reason = null;

        try {
            // Resolve Owner by ownerId or technicalId
            Owner owner = null;
            if (requesterId != null && !requesterId.isBlank()) {
                SearchConditionRequest ownerCondition = SearchConditionRequest.group(
                        "OR",
                        Condition.of("$.ownerId", "EQUALS", requesterId),
                        Condition.of("$.technicalId", "EQUALS", requesterId)
                );
                CompletableFuture<List<DataPayload>> ownerFuture = entityService.getItemsByCondition(
                        Owner.ENTITY_NAME,
                        Owner.ENTITY_VERSION,
                        ownerCondition,
                        true
                );
                List<DataPayload> ownerPayloads = ownerFuture.get();
                if (ownerPayloads != null && !ownerPayloads.isEmpty()) {
                    JsonNode node = ownerPayloads.get(0).getData();
                    owner = objectMapper.treeToValue(node, Owner.class);
                }
            }

            if (owner == null) {
                eligible = false;
                reason = "Requester not found";
            } else {
                String verification = owner.getVerificationStatus();
                if (verification == null || !verification.equalsIgnoreCase("verified")) {
                    eligible = false;
                    reason = "Requester not verified";
                }
            }

            // Resolve Pet by id or technicalId
            Pet pet = null;
            if (petRef != null && !petRef.isBlank()) {
                SearchConditionRequest petCondition = SearchConditionRequest.group(
                        "OR",
                        Condition.of("$.id", "EQUALS", petRef),
                        Condition.of("$.technicalId", "EQUALS", petRef)
                );
                CompletableFuture<List<DataPayload>> petFuture = entityService.getItemsByCondition(
                        Pet.ENTITY_NAME,
                        Pet.ENTITY_VERSION,
                        petCondition,
                        true
                );
                List<DataPayload> petPayloads = petFuture.get();
                if (petPayloads != null && !petPayloads.isEmpty()) {
                    JsonNode node = petPayloads.get(0).getData();
                    pet = objectMapper.treeToValue(node, Pet.class);
                }
            }

            if (pet == null) {
                eligible = false;
                reason = (reason == null) ? "Pet not found" : reason + "; Pet not found";
            } else {
                String status = pet.getStatus();
                if (status == null || !status.equalsIgnoreCase("available")) {
                    eligible = false;
                    reason = (reason == null) ? "Pet not available" : reason + "; Pet not available";
                }
            }

        } catch (Exception ex) {
            logger.error("Error while fetching related entities for adoption request {}: {}", entity.getRequestId(), ex.getMessage(), ex);
            // If we cannot determine eligibility due to errors, mark as under_review to allow manual inspection
            eligible = true;
        }

        String now = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
        if (!eligible) {
            entity.setStatus("rejected");
            entity.setDecisionAt(now);
            logger.info("AdoptionRequest {} marked as REJECTED. Reason: {}", entity.getRequestId(), reason);
        } else {
            // Queue for human review
            entity.setStatus("under_review");
            // decisionAt remains null until decision
            logger.info("AdoptionRequest {} queued for review (UNDER_REVIEW)", entity.getRequestId());
        }

        return entity;
    }
}