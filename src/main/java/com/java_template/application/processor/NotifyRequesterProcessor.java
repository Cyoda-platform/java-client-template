package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class NotifyRequesterProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NotifyRequesterProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

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

        // Only proceed for rejected requests - this processor notifies the requester when a request is rejected
        String status = entity.getStatus();
        if (status == null) {
            logger.debug("AdoptionRequest status is null, skipping notification.");
            return entity;
        }

        if (!"REJECTED".equalsIgnoreCase(status)) {
            logger.debug("AdoptionRequest status is not REJECTED (status={}), skipping notification.", status);
            return entity;
        }

        String requester = entity.getRequesterName() != null ? entity.getRequesterName() : "applicant";
        String contactEmail = entity.getContactEmail();
        String contactPhone = entity.getContactPhone();
        String petId = entity.getPetId();
        String petName = null;

        // Try to enrich notification with pet name if possible
        if (petId != null && !petId.isBlank()) {
            try {
                UUID petUuid = UUID.fromString(petId);
                CompletableFuture<DataPayload> itemFuture = entityService.getItem(petUuid);
                DataPayload payload = itemFuture.get();
                if (payload != null && payload.getData() != null) {
                    JsonNode dataNode = payload.getData();
                    try {
                        Pet pet = objectMapper.treeToValue(dataNode, Pet.class);
                        if (pet != null && pet.getName() != null && !pet.getName().isBlank()) {
                            petName = pet.getName();
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to convert pet payload to Pet object for petId={}: {}", petId, e.getMessage());
                    }
                }
            } catch (IllegalArgumentException iae) {
                logger.debug("petId {} is not a valid UUID: {}", petId, iae.getMessage());
            } catch (InterruptedException | ExecutionException e) {
                logger.warn("Failed to retrieve pet entity for petId={}: {}", petId, e.getMessage());
            } catch (Exception ex) {
                logger.warn("Unexpected error while fetching pet for petId={}: {}", petId, ex.getMessage());
            }
        }

        String petDescriptor = petName != null ? ("pet '" + petName + "'") : (petId != null ? ("pet id " + petId) : "the requested pet");
        String baseMessage = String.format("Hello %s, your adoption request for %s has been rejected.", requester, petDescriptor);
        String notes = entity.getNotes() != null ? entity.getNotes() : "";
        String notificationRecord = String.format("Notification attempt at %s: ", Instant.now().toString());

        boolean notified = false;
        StringBuilder notifDetails = new StringBuilder();

        // Attempt to notify via email if email is present
        if (contactEmail != null && !contactEmail.isBlank()) {
            try {
                String jsonBody = objectMapper.createObjectNode()
                    .put("to", contactEmail)
                    .put("channel", "email")
                    .put("subject", "Adoption request status")
                    .put("message", baseMessage + (entity.getNotes() != null && !entity.getNotes().isBlank() ? " Notes: " + entity.getNotes() : ""))
                    .toString();

                HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://notification.example.local/notify"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

                HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                int statusCode = response.statusCode();
                if (statusCode >= 200 && statusCode < 300) {
                    notifDetails.append("email sent");
                    notified = true;
                } else {
                    notifDetails.append("email failed(status=").append(statusCode).append(")");
                    logger.warn("Email notification returned non-success status for AdoptionRequest id {}: {}", entity.getId(), statusCode);
                }
            } catch (Exception e) {
                notifDetails.append("email error:").append(e.getMessage());
                logger.warn("Failed to send email notification for AdoptionRequest id {}: {}", entity.getId(), e.getMessage());
            }
        } else {
            notifDetails.append("no_email");
        }

        // If email not available or failed, attempt SMS/phone notification if phone present
        if ((!notified) && contactPhone != null && !contactPhone.isBlank()) {
            try {
                String jsonBody = objectMapper.createObjectNode()
                    .put("to", contactPhone)
                    .put("channel", "sms")
                    .put("message", baseMessage)
                    .toString();

                HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://notification.example.local/notify"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

                HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                int statusCode = response.statusCode();
                if (statusCode >= 200 && statusCode < 300) {
                    if (notifDetails.length() > 0) notifDetails.append("; ");
                    notifDetails.append("sms sent");
                    notified = true;
                } else {
                    if (notifDetails.length() > 0) notifDetails.append("; ");
                    notifDetails.append("sms failed(status=").append(statusCode).append(")");
                    logger.warn("SMS notification returned non-success status for AdoptionRequest id {}: {}", entity.getId(), statusCode);
                }
            } catch (Exception e) {
                if (notifDetails.length() > 0) notifDetails.append("; ");
                notifDetails.append("sms error:").append(e.getMessage());
                logger.warn("Failed to send SMS notification for AdoptionRequest id {}: {}", entity.getId(), e.getMessage());
            }
        } else {
            if (!notified) {
                if (notifDetails.length() > 0) notifDetails.append("; ");
                notifDetails.append("no_phone");
            }
        }

        // Update the adoption request notes with notification attempt result
        String updatedNotes = notes;
        if (!updatedNotes.isBlank()) {
            updatedNotes = updatedNotes + " | ";
        }
        updatedNotes = updatedNotes + notificationRecord + notifDetails.toString();
        entity.setNotes(updatedNotes);

        logger.info("NotifyRequesterProcessor completed for AdoptionRequest id {}. Notification details: {}", entity.getId(), notifDetails.toString());

        return entity;
    }
}