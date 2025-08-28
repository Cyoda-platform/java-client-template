package com.java_template.application.processor;

import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
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

        try {
            // Default paymentStatus to NOT_PAID if missing/blank
            String paymentStatus = (String) getFieldValue(adoptionRequest, "paymentStatus");
            if (paymentStatus == null || paymentStatus.isBlank()) {
                setFieldValue(adoptionRequest, "paymentStatus", "NOT_PAID");
            }

            // Basic sanity checks for referenced IDs
            String petId = (String) getFieldValue(adoptionRequest, "petId");
            String userId = (String) getFieldValue(adoptionRequest, "userId");
            String requestId = (String) getFieldValue(adoptionRequest, "requestId");

            if (petId == null || petId.isBlank()) {
                setFieldValue(adoptionRequest, "status", "REJECTED");
                setFieldValue(adoptionRequest, "notes", concatNotes((String) getFieldValue(adoptionRequest, "notes"), "PetId is missing"));
                logger.warn("AdoptionRequest {} rejected: missing petId", safeString(requestId));
                return adoptionRequest;
            }
            if (userId == null || userId.isBlank()) {
                setFieldValue(adoptionRequest, "status", "REJECTED");
                setFieldValue(adoptionRequest, "notes", concatNotes((String) getFieldValue(adoptionRequest, "notes"), "UserId is missing"));
                logger.warn("AdoptionRequest {} rejected: missing userId", safeString(requestId));
                return adoptionRequest;
            }

            // Attempt to load referenced Pet and User entities by their technical UUIDs.
            JsonNode petData = null;
            JsonNode userData = null;

            try {
                UUID petUuid = UUID.fromString(petId);
                CompletableFuture<DataPayload> petFuture = entityService.getItem(petUuid);
                DataPayload petPayload = petFuture != null ? petFuture.get() : null;
                if (petPayload == null || petPayload.getData() == null) {
                    setFieldValue(adoptionRequest, "status", "REJECTED");
                    setFieldValue(adoptionRequest, "notes", concatNotes((String) getFieldValue(adoptionRequest, "notes"), "Referenced pet not found"));
                    logger.warn("AdoptionRequest {} rejected: pet not found ({})", safeString(requestId), petId);
                    return adoptionRequest;
                }
                petData = petPayload.getData();
            } catch (IllegalArgumentException iae) {
                setFieldValue(adoptionRequest, "status", "REJECTED");
                setFieldValue(adoptionRequest, "notes", concatNotes((String) getFieldValue(adoptionRequest, "notes"), "Invalid petId format"));
                logger.warn("AdoptionRequest {} rejected: invalid petId format: {}", safeString(requestId), iae.getMessage());
                return adoptionRequest;
            } catch (InterruptedException | ExecutionException ex) {
                logger.error("Error fetching pet for adoptionRequest {}: {}", safeString(requestId), ex.getMessage(), ex);
                setFieldValue(adoptionRequest, "status", "REJECTED");
                setFieldValue(adoptionRequest, "notes", concatNotes((String) getFieldValue(adoptionRequest, "notes"), "Error fetching pet information"));
                return adoptionRequest;
            } catch (Exception ex) {
                logger.error("Unexpected error converting pet payload: {}", ex.getMessage(), ex);
                setFieldValue(adoptionRequest, "status", "REJECTED");
                setFieldValue(adoptionRequest, "notes", concatNotes((String) getFieldValue(adoptionRequest, "notes"), "Error processing pet information"));
                return adoptionRequest;
            }

            try {
                UUID userUuid = UUID.fromString(userId);
                CompletableFuture<DataPayload> userFuture = entityService.getItem(userUuid);
                DataPayload userPayload = userFuture != null ? userFuture.get() : null;
                if (userPayload == null || userPayload.getData() == null) {
                    setFieldValue(adoptionRequest, "status", "REJECTED");
                    setFieldValue(adoptionRequest, "notes", concatNotes((String) getFieldValue(adoptionRequest, "notes"), "Referenced user not found"));
                    logger.warn("AdoptionRequest {} rejected: user not found ({})", safeString(requestId), userId);
                    return adoptionRequest;
                }
                userData = userPayload.getData();
            } catch (IllegalArgumentException iae) {
                setFieldValue(adoptionRequest, "status", "REJECTED");
                setFieldValue(adoptionRequest, "notes", concatNotes((String) getFieldValue(adoptionRequest, "notes"), "Invalid userId format"));
                logger.warn("AdoptionRequest {} rejected: invalid userId format: {}", safeString(requestId), iae.getMessage());
                return adoptionRequest;
            } catch (InterruptedException | ExecutionException ex) {
                logger.error("Error fetching user for adoptionRequest {}: {}", safeString(requestId), ex.getMessage(), ex);
                setFieldValue(adoptionRequest, "status", "REJECTED");
                setFieldValue(adoptionRequest, "notes", concatNotes((String) getFieldValue(adoptionRequest, "notes"), "Error fetching user information"));
                return adoptionRequest;
            } catch (Exception ex) {
                logger.error("Unexpected error converting user payload: {}", ex.getMessage(), ex);
                setFieldValue(adoptionRequest, "status", "REJECTED");
                setFieldValue(adoptionRequest, "notes", concatNotes((String) getFieldValue(adoptionRequest, "notes"), "Error processing user information"));
                return adoptionRequest;
            }

            // Business rule: Pet must be Available
            String petStatus = extractStringField(petData, "status");
            if (petStatus == null || !"Available".equalsIgnoreCase(petStatus)) {
                setFieldValue(adoptionRequest, "status", "REJECTED");
                setFieldValue(adoptionRequest, "notes", concatNotes((String) getFieldValue(adoptionRequest, "notes"), "Pet is not available for adoption"));
                logger.info("AdoptionRequest {} rejected: pet {} not available (status={})", safeString(requestId), petId, safeString(petStatus));
                return adoptionRequest;
            }

            // Business rule: User must be eligible (Active or Trusted)
            String userStatus = extractStringField(userData, "status");
            if (userStatus == null ||
                !(userStatus.equalsIgnoreCase("Active") || userStatus.equalsIgnoreCase("Trusted"))) {
                setFieldValue(adoptionRequest, "status", "REJECTED");
                setFieldValue(adoptionRequest, "notes", concatNotes((String) getFieldValue(adoptionRequest, "notes"), "User is not eligible to request adoption"));
                logger.info("AdoptionRequest {} rejected: user {} not eligible (status={})", safeString(requestId), userId, safeString(userStatus));
                return adoptionRequest;
            }

            // All validations passed -> mark for review
            setFieldValue(adoptionRequest, "status", "PENDING_REVIEW");
            if (getFieldValue(adoptionRequest, "notes") == null || ((String) getFieldValue(adoptionRequest, "notes")).isBlank()) {
                setFieldValue(adoptionRequest, "notes", "Validated and assigned for review");
            } else {
                setFieldValue(adoptionRequest, "notes", concatNotes((String) getFieldValue(adoptionRequest, "notes"), "Validated and assigned for review"));
            }
            logger.info("AdoptionRequest {} validated successfully and moved to PENDING_REVIEW", safeString(requestId));

            return adoptionRequest;
        } catch (ReflectiveOperationException roe) {
            logger.error("Reflection error processing AdoptionRequest: {}", roe.getMessage(), roe);
            try {
                setFieldValue(adoptionRequest, "status", "REJECTED");
                setFieldValue(adoptionRequest, "notes", concatNotes((String) getFieldValue(adoptionRequest, "notes"), "Processing error"));
            } catch (Exception ignore) {}
            return adoptionRequest;
        } catch (Exception ex) {
            logger.error("Unexpected error in ValidateRequestProcessor: {}", ex.getMessage(), ex);
            try {
                setFieldValue(adoptionRequest, "status", "REJECTED");
                setFieldValue(adoptionRequest, "notes", concatNotes((String) getFieldValue(adoptionRequest, "notes"), "Unexpected processing error"));
            } catch (Exception ignore) {}
            return adoptionRequest;
        }
    }

    // Helper: safely get a private field value via reflection
    private Object getFieldValue(Object target, String fieldName) throws ReflectiveOperationException {
        if (target == null) return null;
        Field field = getDeclaredFieldRecursive(target.getClass(), fieldName);
        if (field == null) return null;
        field.setAccessible(true);
        return field.get(target);
    }

    // Helper: safely set a private field value via reflection
    private void setFieldValue(Object target, String fieldName, Object value) throws ReflectiveOperationException {
        if (target == null) return;
        Field field = getDeclaredFieldRecursive(target.getClass(), fieldName);
        if (field == null) return;
        field.setAccessible(true);
        field.set(target, value);
    }

    // Search declared field in class hierarchy
    private Field getDeclaredFieldRecursive(Class<?> clazz, String fieldName) {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException nsf) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private String extractStringField(JsonNode node, String fieldName) {
        if (node == null) return null;
        JsonNode v = node.get(fieldName);
        return v != null && !v.isNull() ? v.asText(null) : null;
    }

    private String concatNotes(String existing, String addition) {
        if (existing == null || existing.isBlank()) return addition;
        return existing + " | " + addition;
    }

    private String safeString(Object o) {
        return o == null ? "null" : o.toString();
    }
}