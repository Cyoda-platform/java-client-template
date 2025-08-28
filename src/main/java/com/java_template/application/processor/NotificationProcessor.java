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

@Component
public class NotificationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NotificationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public NotificationProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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
            String status = entity.getStatus() != null ? entity.getStatus().trim().toLowerCase() : "";

            // Set processedAt if the request has been processed but processedAt is not set.
            if ((status.equals("approved") || status.equals("rejected") || status.equals("completed"))
                    && (entity.getProcessedAt() == null || entity.getProcessedAt().isBlank())) {
                entity.setProcessedAt(Instant.now().toString());
                // processedBy can be set to system identifier if not present
                if (entity.getProcessedBy() == null || entity.getProcessedBy().isBlank()) {
                    entity.setProcessedBy("notification-processor");
                }
            }

            // Only send notifications for final decision statuses: approved, rejected, completed
            if (!(status.equals("approved") || status.equals("rejected") || status.equals("completed"))) {
                logger.info("AdoptionRequest {} status '{}' not eligible for notification. Skipping.", entity.getId(), status);
                return entity;
            }

            // Fetch related Pet
            Pet pet = null;
            try {
                SearchConditionRequest petCondition = SearchConditionRequest.group("AND",
                        Condition.of("$.id", "EQUALS", entity.getPetId())
                );
                CompletableFuture<List<DataPayload>> petFuture = entityService.getItemsByCondition(
                        Pet.ENTITY_NAME, Pet.ENTITY_VERSION, petCondition, true
                );
                List<DataPayload> petPayloads = petFuture.get();
                if (petPayloads != null && !petPayloads.isEmpty()) {
                    DataPayload payload = petPayloads.get(0);
                    pet = objectMapper.treeToValue(payload.getData(), Pet.class);
                }
            } catch (Exception ex) {
                logger.warn("Failed to fetch Pet for adoption request {}: {}", entity.getId(), ex.getMessage());
            }

            // Fetch requester Owner
            Owner requester = null;
            try {
                SearchConditionRequest ownerCondition = SearchConditionRequest.group("AND",
                        Condition.of("$.id", "EQUALS", entity.getRequesterId())
                );
                CompletableFuture<List<DataPayload>> ownerFuture = entityService.getItemsByCondition(
                        Owner.ENTITY_NAME, Owner.ENTITY_VERSION, ownerCondition, true
                );
                List<DataPayload> ownerPayloads = ownerFuture.get();
                if (ownerPayloads != null && !ownerPayloads.isEmpty()) {
                    DataPayload payload = ownerPayloads.get(0);
                    requester = objectMapper.treeToValue(payload.getData(), Owner.class);
                }
            } catch (Exception ex) {
                logger.warn("Failed to fetch Owner for adoption request {}: {}", entity.getId(), ex.getMessage());
            }

            // Build notification message
            String petName = pet != null && pet.getName() != null ? pet.getName() : entity.getPetId();
            String ownerName = requester != null && requester.getFullName() != null ? requester.getFullName() : entity.getRequesterId();

            String subject;
            String body;

            if (status.equals("approved")) {
                subject = "Adoption Approved";
                body = String.format("Good news %s! Your adoption request for '%s' has been approved. Request id: %s",
                        ownerName, petName, entity.getId());
            } else if (status.equals("rejected")) {
                subject = "Adoption Request Rejected";
                body = String.format("Hello %s, we are sorry to inform you that your adoption request for '%s' has been rejected. Request id: %s",
                        ownerName, petName, entity.getId());
            } else { // completed or other final state
                subject = "Adoption Request Completed";
                body = String.format("Hello %s, your adoption request for '%s' has been completed. Request id: %s",
                        ownerName, petName, entity.getId());
            }

            // Simulate sending notification: log the notification and include details that would be sent.
            // Real implementation would call Cyoda notification action or external service.
            logger.info("Notification prepared for AdoptionRequest {}: subject='{}', body='{}', to='{}'",
                    entity.getId(), subject, body, requester != null ? requester.getEmail() : "unknown");

            // Optionally, we could append a note to the adoption request message (augmenting the entity state)
            String existingMsg = entity.getMessage() != null ? entity.getMessage() : "";
            String note = String.format("[NotificationSent:%s at %s]", status.toUpperCase(), Instant.now().toString());
            entity.setMessage((existingMsg.isBlank() ? "" : existingMsg + " ") + note);

        } catch (Exception e) {
            logger.error("Error while processing NotificationProcessor for AdoptionRequest {}: {}", entity.getId(), e.getMessage(), e);
        }

        return entity;
    }
}