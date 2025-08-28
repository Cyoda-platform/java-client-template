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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
        String status = getStringProperty(entity, new String[]{"getStatus", "status"});
        if (status == null) {
            logger.debug("AdoptionRequest status is null, skipping notification.");
            return entity;
        }

        if (!"REJECTED".equalsIgnoreCase(status)) {
            logger.debug("AdoptionRequest status is not REJECTED (status={}), skipping notification.", status);
            return entity;
        }

        String requester = null;
        try {
            Method m = entity.getClass().getMethod("getRequesterName");
            Object val = m.invoke(entity);
            requester = val != null ? val.toString() : null;
        } catch (Exception e) {
            // fallback to null
            requester = null;
        }
        requester = requester != null ? requester : "applicant";

        String contactEmail = null;
        try {
            Method m = entity.getClass().getMethod("getContactEmail");
            Object val = m.invoke(entity);
            contactEmail = val != null ? val.toString() : null;
        } catch (Exception ignored) {
        }

        String contactPhone = null;
        try {
            Method m = entity.getClass().getMethod("getContactPhone");
            Object val = m.invoke(entity);
            contactPhone = val != null ? val.toString() : null;
        } catch (Exception ignored) {
        }

        String petId = null;
        try {
            Method m = entity.getClass().getMethod("getPetId");
            Object val = m.invoke(entity);
            petId = val != null ? val.toString() : null;
        } catch (Exception ignored) {
        }

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

        String notes = getStringProperty(entity, new String[]{"getNotes", "notes"});
        notes = notes != null ? notes : "";

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
                    .put("message", baseMessage + (notes != null && !notes.isBlank() ? " Notes: " + notes : ""))
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
                    try {
                        Method idMethod = entity.getClass().getMethod("getId");
                        Object idVal = idMethod.invoke(entity);
                        logger.warn("Email notification returned non-success status for AdoptionRequest id {}: {}", idVal, statusCode);
                    } catch (Exception e) {
                        logger.warn("Email notification returned non-success status for AdoptionRequest (id unavailable): {}", statusCode);
                    }
                }
            } catch (Exception e) {
                notifDetails.append("email error:").append(e.getMessage());
                try {
                    Method idMethod = entity.getClass().getMethod("getId");
                    Object idVal = idMethod.invoke(entity);
                    logger.warn("Failed to send email notification for AdoptionRequest id {}: {}", idVal, e.getMessage());
                } catch (Exception ex) {
                    logger.warn("Failed to send email notification for AdoptionRequest (id unavailable): {}", e.getMessage());
                }
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
                    try {
                        Method idMethod = entity.getClass().getMethod("getId");
                        Object idVal = idMethod.invoke(entity);
                        logger.warn("SMS notification returned non-success status for AdoptionRequest id {}: {}", idVal, statusCode);
                    } catch (Exception e) {
                        logger.warn("SMS notification returned non-success status for AdoptionRequest (id unavailable): {}", statusCode);
                    }
                }
            } catch (Exception e) {
                if (notifDetails.length() > 0) notifDetails.append("; ");
                notifDetails.append("sms error:").append(e.getMessage());
                try {
                    Method idMethod = entity.getClass().getMethod("getId");
                    Object idVal = idMethod.invoke(entity);
                    logger.warn("Failed to send SMS notification for AdoptionRequest id {}: {}", idVal, e.getMessage());
                } catch (Exception ex) {
                    logger.warn("Failed to send SMS notification for AdoptionRequest (id unavailable): {}", e.getMessage());
                }
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
        setStringProperty(entity, "setNotes", "notes", updatedNotes);

        try {
            Method idMethod = entity.getClass().getMethod("getId");
            Object idVal = idMethod.invoke(entity);
            logger.info("NotifyRequesterProcessor completed for AdoptionRequest id {}. Notification details: {}", idVal, notifDetails.toString());
        } catch (Exception e) {
            logger.info("NotifyRequesterProcessor completed for AdoptionRequest (id unavailable). Notification details: {}", notifDetails.toString());
        }

        return entity;
    }

    private String getStringProperty(Object obj, String[] candidates) {
        if (obj == null) return null;
        for (String name : candidates) {
            // try method with no args
            try {
                Method m = obj.getClass().getMethod(name);
                Object val = m.invoke(obj);
                if (val != null) return val.toString();
            } catch (NoSuchMethodException ignored) {
            } catch (Exception e) {
                logger.debug("Error invoking method {} on {}: {}", name, obj.getClass().getSimpleName(), e.getMessage());
            }
        }
        // try fields matching candidate names
        for (String name : candidates) {
            try {
                Field f = obj.getClass().getDeclaredField(name);
                f.setAccessible(true);
                Object val = f.get(obj);
                if (val != null) return val.toString();
            } catch (NoSuchFieldException ignored) {
            } catch (Exception e) {
                logger.debug("Error accessing field {} on {}: {}", name, obj.getClass().getSimpleName(), e.getMessage());
            }
        }
        return null;
    }

    private void setStringProperty(Object obj, String methodName, String fieldName, String value) {
        if (obj == null) return;
        try {
            Method m = obj.getClass().getMethod(methodName, String.class);
            m.invoke(obj, value);
            return;
        } catch (NoSuchMethodException ignored) {
        } catch (Exception e) {
            logger.debug("Error invoking setter {} on {}: {}", methodName, obj.getClass().getSimpleName(), e.getMessage());
        }
        try {
            Field f = obj.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(obj, value);
        } catch (NoSuchFieldException ignored) {
        } catch (Exception e) {
            logger.debug("Error setting field {} on {}: {}", fieldName, obj.getClass().getSimpleName(), e.getMessage());
        }
    }
}