package com.java_template.application.processor;

import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

@Component
public class ArchivePetProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ArchivePetProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ArchivePetProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Pet for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Pet.class)
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

    private boolean isValidEntity(Pet entity) {
        return entity != null && entity.isValid();
    }

    private Pet processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Pet> context) {
        Pet entity = context.entity();
        try {
            logger.info("ArchivePetProcessor starting for pet id: {}", getEntityId(entity));

            String currentStatus = entity.getStatus();
            if (currentStatus == null) {
                logger.warn("Pet status is null for id: {}, setting to ARCHIVED", getEntityId(entity));
                setEntityStatus(entity, "ARCHIVED");
            } else if ("ARCHIVED".equalsIgnoreCase(currentStatus)) {
                // Already archived, nothing to do
                logger.info("Pet id: {} is already archived.", getEntityId(entity));
                return entity;
            } else if (!"ADOPTED".equalsIgnoreCase(currentStatus)) {
                // Business rule: only archive pets that are ADOPTED (manual archive)
                logger.warn("Pet id: {} has status '{}' and is not eligible for archiving. Marking as ARCHIVED anyway per manual action.", getEntityId(entity), currentStatus);
                setEntityStatus(entity, "ARCHIVED");
            } else {
                // Normal flow: ADOPTED -> ARCHIVED
                logger.info("Archiving adopted pet id: {}", getEntityId(entity));
                setEntityStatus(entity, "ARCHIVED");
            }

            // Add an 'archived' tag if not present
            List<String> tags = entity.getTags();
            if (tags == null) {
                tags = new ArrayList<>();
                tags.add("archived");
                entity.setTags(tags);
            } else {
                boolean hasArchived = false;
                for (String t : tags) {
                    if ("archived".equalsIgnoreCase(t)) {
                        hasArchived = true;
                        break;
                    }
                }
                if (!hasArchived) {
                    tags.add("archived");
                }
            }

            // Optionally append a short note to healthNotes to indicate archival (preserve existing notes)
            String notes = entity.getHealthNotes();
            String archiveNote = "Archived by system.";
            if (notes == null || notes.isBlank()) {
                entity.setHealthNotes(archiveNote);
            } else if (!notes.contains(archiveNote)) {
                entity.setHealthNotes(notes + " " + archiveNote);
            }

            logger.info("Pet id: {} archived successfully.", getEntityId(entity));
        } catch (Exception ex) {
            logger.error("Error while processing ArchivePetProcessor for pet id: {}", getEntityId(entity), ex);
            // In case of unexpected errors, leave entity in current state but attach a generic note
            if (entity != null) {
                String notes = entity.getHealthNotes();
                String errNote = "Archive processing encountered an error.";
                if (notes == null || notes.isBlank()) {
                    entity.setHealthNotes(errNote);
                } else if (!notes.contains(errNote)) {
                    entity.setHealthNotes(notes + " " + errNote);
                }
            }
        }
        return entity;
    }

    private String getEntityId(Pet entity) {
        if (entity == null) {
            return "unknown";
        }
        try {
            java.lang.reflect.Method m = entity.getClass().getMethod("getId");
            Object id = m.invoke(entity);
            return id != null ? id.toString() : "unknown";
        } catch (NoSuchMethodException e) {
            // try alternative common names
            try {
                java.lang.reflect.Method m = entity.getClass().getMethod("getPetId");
                Object id = m.invoke(entity);
                return id != null ? id.toString() : "unknown";
            } catch (Exception ex) {
                try {
                    java.lang.reflect.Method m = entity.getClass().getMethod("getTechnicalId");
                    Object id = m.invoke(entity);
                    return id != null ? id.toString() : "unknown";
                } catch (Exception exc) {
                    return "unknown";
                }
            }
        } catch (Exception e) {
            return "unknown";
        }
    }

    private void setEntityStatus(Pet entity, String status) {
        if (entity == null) {
            return;
        }
        try {
            java.lang.reflect.Method m = entity.getClass().getMethod("setStatus", String.class);
            m.invoke(entity, status);
            return;
        } catch (NoSuchMethodException e) {
            // try alternative setter names
            try {
                java.lang.reflect.Method m = entity.getClass().getMethod("setState", String.class);
                m.invoke(entity, status);
                return;
            } catch (Exception ex) {
                // try direct field set
                try {
                    java.lang.reflect.Field f = entity.getClass().getDeclaredField("status");
                    f.setAccessible(true);
                    f.set(entity, status);
                    return;
                } catch (Exception exc) {
                    logger.warn("Unable to set status on entity via reflection for id: {}", getEntityId(entity));
                }
            }
        } catch (Exception e) {
            logger.warn("Unable to set status on entity for id: {} due to exception.", getEntityId(entity), e);
        }
    }
}