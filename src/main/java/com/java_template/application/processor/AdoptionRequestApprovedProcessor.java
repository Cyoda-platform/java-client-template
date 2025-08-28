package com.java_template.application.processor;

import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.lang.reflect.Field;

@Component
public class AdoptionRequestApprovedProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AdoptionRequestApprovedProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public AdoptionRequestApprovedProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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
        if (entity == null) {
            logger.warn("AdoptionRequest entity is null in execution context");
            return entity;
        }

        // Business logic:
        // 1. When an adoption request is approved (this processor), set the request status to APPROVED.
        // 2. Attempt to set the referenced Pet status to PENDING_ADOPTION only if the Pet exists and is currently AVAILABLE.
        // 3. If Pet does not exist or is not AVAILABLE, mark the request as REJECTED and note reason.

        // Set status to APPROVED (use reflective field set in case setter is not present)
        setEntityField(entity, "status", "APPROVED");

        String petId = entity.getPetId();
        if (petId == null || petId.isBlank()) {
            logger.error("AdoptionRequest missing petId, rejecting request id={}", getEntityFieldAsString(entity, "id"));
            setEntityField(entity, "status", "REJECTED");
            if (hasField(entity, "notes")) {
                setEntityField(entity, "notes", "PetId missing for approval");
            } else {
                entity.setNotes("PetId missing for approval");
            }
            return entity;
        }

        UUID petUuid;
        try {
            petUuid = UUID.fromString(petId);
        } catch (IllegalArgumentException ex) {
            // petId is not a valid UUID - cannot proceed
            logger.error("Invalid petId format for AdoptionRequest id={}: {}", getEntityFieldAsString(entity, "id"), petId);
            setEntityField(entity, "status", "REJECTED");
            if (hasField(entity, "notes")) {
                setEntityField(entity, "notes", "Invalid petId format");
            } else {
                entity.setNotes("Invalid petId format");
            }
            return entity;
        }

        try {
            // Fetch pet by technical id
            CompletableFuture<DataPayload> petFuture = entityService.getItem(petUuid);
            DataPayload petPayload = petFuture.get();
            if (petPayload == null || petPayload.getData() == null) {
                logger.warn("Referenced pet not found for AdoptionRequest id={}, petId={}", getEntityFieldAsString(entity, "id"), petId);
                setEntityField(entity, "status", "REJECTED");
                if (hasField(entity, "notes")) {
                    setEntityField(entity, "notes", "Referenced pet not found");
                } else {
                    entity.setNotes("Referenced pet not found");
                }
                return entity;
            }

            // Map payload data to Pet
            Pet pet = objectMapper.treeToValue(petPayload.getData(), Pet.class);
            if (pet == null) {
                logger.warn("Unable to convert pet payload to Pet object for petId={}", petId);
                setEntityField(entity, "status", "REJECTED");
                if (hasField(entity, "notes")) {
                    setEntityField(entity, "notes", "Failed to retrieve pet data");
                } else {
                    entity.setNotes("Failed to retrieve pet data");
                }
                return entity;
            }

            String petStatus = pet.getStatus();
            if (petStatus != null && petStatus.equalsIgnoreCase("AVAILABLE")) {
                // Update pet to pending adoption
                pet.setStatus("PENDING_ADOPTION");
                try {
                    CompletableFuture<UUID> updated = entityService.updateItem(petUuid, pet);
                    UUID updatedId = updated.get();
                    logger.info("Pet {} status updated to PENDING_ADOPTION (update id={}) for adoption request id={}", petId, updatedId, getEntityFieldAsString(entity, "id"));
                    if (hasField(entity, "notes")) {
                        setEntityField(entity, "notes", "Pet status set to PENDING_ADOPTION");
                    } else {
                        entity.setNotes("Pet status set to PENDING_ADOPTION");
                    }
                    // Leave entity.status as APPROVED
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    logger.error("Interrupted while updating pet for AdoptionRequest id={}", getEntityFieldAsString(entity, "id"), ie);
                    setEntityField(entity, "status", "REJECTED");
                    if (hasField(entity, "notes")) {
                        setEntityField(entity, "notes", "Internal error while updating pet");
                    } else {
                        entity.setNotes("Internal error while updating pet");
                    }
                } catch (ExecutionException ee) {
                    logger.error("Failed to update pet for AdoptionRequest id={}", getEntityFieldAsString(entity, "id"), ee);
                    setEntityField(entity, "status", "REJECTED");
                    if (hasField(entity, "notes")) {
                        setEntityField(entity, "notes", "Failed to update pet status");
                    } else {
                        entity.setNotes("Failed to update pet status");
                    }
                }
            } else {
                logger.warn("Pet not available for adoption (current status='{}') for petId={}, rejecting request id={}", petStatus, petId, getEntityFieldAsString(entity, "id"));
                setEntityField(entity, "status", "REJECTED");
                String reason = "Pet not available for adoption. Current status: " + (petStatus == null ? "unknown" : petStatus);
                if (hasField(entity, "notes")) {
                    setEntityField(entity, "notes", reason);
                } else {
                    entity.setNotes(reason);
                }
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while fetching pet for AdoptionRequest id={}", getEntityFieldAsString(entity, "id"), ie);
            setEntityField(entity, "status", "REJECTED");
            if (hasField(entity, "notes")) {
                setEntityField(entity, "notes", "Internal error while fetching pet");
            } else {
                entity.setNotes("Internal error while fetching pet");
            }
        } catch (ExecutionException ee) {
            logger.error("Error fetching pet for AdoptionRequest id={}", getEntityFieldAsString(entity, "id"), ee);
            setEntityField(entity, "status", "REJECTED");
            if (hasField(entity, "notes")) {
                setEntityField(entity, "notes", "Error fetching pet information");
            } else {
                entity.setNotes("Error fetching pet information");
            }
        } catch (Exception ex) {
            logger.error("Unexpected error processing AdoptionRequest id={}", getEntityFieldAsString(entity, "id"), ex);
            setEntityField(entity, "status", "REJECTED");
            if (hasField(entity, "notes")) {
                setEntityField(entity, "notes", "Unexpected error processing approval");
            } else {
                entity.setNotes("Unexpected error processing approval");
            }
        }

        return entity;
    }

    private void setEntityField(Object entity, String fieldName, Object value) {
        try {
            Field field = findField(entity.getClass(), fieldName);
            if (field == null) {
                logger.debug("Field '{}' not present on {}; skipping set", fieldName, entity.getClass().getName());
                return;
            }
            field.setAccessible(true);
            field.set(entity, value);
        } catch (IllegalAccessException iae) {
            logger.warn("Unable to set field '{}' on {}: {}", fieldName, entity.getClass().getName(), iae.getMessage());
        } catch (Exception e) {
            logger.warn("Unexpected error setting field '{}' on {}: {}", fieldName, entity.getClass().getName(), e.getMessage());
        }
    }

    private String getEntityFieldAsString(Object entity, String fieldName) {
        try {
            Field field = findField(entity.getClass(), fieldName);
            if (field == null) {
                return null;
            }
            field.setAccessible(true);
            Object val = field.get(entity);
            return val == null ? null : val.toString();
        } catch (IllegalAccessException iae) {
            logger.warn("Unable to access field '{}' on {}: {}", fieldName, entity.getClass().getName(), iae.getMessage());
            return null;
        } catch (Exception e) {
            logger.warn("Unexpected error accessing field '{}' on {}: {}", fieldName, entity.getClass().getName(), e.getMessage());
            return null;
        }
    }

    private boolean hasField(Object entity, String fieldName) {
        return findField(entity.getClass(), fieldName) != null;
    }

    private Field findField(Class<?> clazz, String fieldName) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}