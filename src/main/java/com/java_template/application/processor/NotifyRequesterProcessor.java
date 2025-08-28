package com.java_template.application.processor;

import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
import com.java_template.application.entity.owner.version_1.Owner;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.service.EntityService;

import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.cyoda.cloud.api.event.common.DataPayload;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class NotifyRequesterProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NotifyRequesterProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public NotifyRequesterProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing AdoptionRequest for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(AdoptionRequest.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
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
        // Notify the requester when the adoption request reaches COMPLETED state.
        // To create a meaningful notification we attempt to read Owner and Pet info by requesterId and petId.
        // We send a notification payload to an external notification service (HTTP).
        // Do not modify the triggering AdoptionRequest entity here.

        try {
            String status = entity.getStatus();
            if (status == null) {
                logger.warn("AdoptionRequest {} has null status, skipping notification", entity.getRequestId());
                return entity;
            }

            if (!"COMPLETED".equalsIgnoreCase(status)) {
                // Only notify on COMPLETED state
                logger.info("AdoptionRequest {} status is '{}', no notification required", entity.getRequestId(), status);
                return entity;
            }

            String requesterId = entity.getRequesterId();
            String petId = entity.getPetId();

            Owner owner = null;
            Pet pet = null;

            // Fetch Owner by ownerId == requesterId
            if (requesterId != null && !requesterId.isBlank()) {
                try {
                    SearchConditionRequest ownerCondition = SearchConditionRequest.group("AND",
                        Condition.of("$.ownerId", "EQUALS", requesterId)
                    );
                    CompletableFuture<List<DataPayload>> ownersFuture = entityService.getItemsByCondition(Owner.ENTITY_NAME, Owner.ENTITY_VERSION, ownerCondition, true);
                    List<DataPayload> ownerPayloads = ownersFuture.get();
                    if (ownerPayloads != null && !ownerPayloads.isEmpty()) {
                        DataPayload payload = ownerPayloads.get(0);
                        owner = objectMapper.treeToValue(payload.getData(), Owner.class);
                    } else {
                        logger.warn("No Owner found for requesterId {}", requesterId);
                    }
                } catch (Exception ex) {
                    logger.error("Failed to load Owner for requesterId {}: {}", requesterId, ex.getMessage(), ex);
                }
            } else {
                logger.warn("AdoptionRequest {} has no requesterId", entity.getRequestId());
            }

            // Fetch Pet by id == petId (external/source id)
            if (petId != null && !petId.isBlank()) {
                try {
                    SearchConditionRequest petCondition = SearchConditionRequest.group("AND",
                        Condition.of("$.id", "EQUALS", petId)
                    );
                    CompletableFuture<List<DataPayload>> petsFuture = entityService.getItemsByCondition(Pet.ENTITY_NAME, Pet.ENTITY_VERSION, petCondition, true);
                    List<DataPayload> petPayloads = petsFuture.get();
                    if (petPayloads != null && !petPayloads.isEmpty()) {
                        DataPayload payload = petPayloads.get(0);
                        pet = objectMapper.treeToValue(payload.getData(), Pet.class);
                    } else {
                        logger.warn("No Pet found for petId {}", petId);
                    }
                } catch (Exception ex) {
                    logger.error("Failed to load Pet for petId {}: {}", petId, ex.getMessage(), ex);
                }
            } else {
                logger.warn("AdoptionRequest {} has no petId", entity.getRequestId());
            }

            // Build notification payload
            ObjectNode notification = objectMapper.createObjectNode();
            if (owner != null && owner.getContactEmail() != null) {
                notification.put("toEmail", owner.getContactEmail());
            } else {
                notification.putNull("toEmail");
            }
            if (owner != null && owner.getContactPhone() != null) {
                notification.put("toPhone", owner.getContactPhone());
            } else {
                notification.putNull("toPhone");
            }

            String petName = pet != null && pet.getName() != null ? pet.getName() : "the pet";
            notification.put("subject", "Your adoption request " + entity.getRequestId() + " is completed");
            notification.put("message", "Congratulations! Your adoption request for " + petName + " has been completed.");
            notification.put("requestId", entity.getRequestId());
            notification.put("petId", petId != null ? petId : "");
            notification.put("requesterId", requesterId != null ? requesterId : "");

            // Send to external notification service
            try {
                String notificationServiceUrl = "http://notification-service/notify"; // configurable in real app
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(notificationServiceUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(notification)))
                    .build();

                HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                logger.info("Notification sent for AdoptionRequest {}. Notification service responded with status {}", entity.getRequestId(), response.statusCode());
            } catch (Exception ex) {
                logger.error("Failed to send notification for AdoptionRequest {}: {}", entity.getRequestId(), ex.getMessage(), ex);
            }

        } catch (Exception ex) {
            logger.error("Unexpected error while processing notification for AdoptionRequest {}: {}", entity.getRequestId(), ex.getMessage(), ex);
        }

        return entity;
    }
}