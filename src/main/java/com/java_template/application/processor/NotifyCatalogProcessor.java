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

import org.springframework.beans.factory.annotation.Autowired;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

@Component
public class NotifyCatalogProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NotifyCatalogProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public NotifyCatalogProcessor(SerializerFactory serializerFactory,
                                  EntityService entityService,
                                  ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Pet for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Pet.class)
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

    private boolean isValidEntity(Pet entity) {
        return entity != null && entity.isValid();
    }

    private Pet processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Pet> context) {
        Pet entity = context.entity();

        try {
            // Business rule:
            // - Only notify catalog when pet is AVAILABLE
            // - Normalize species and breed fields for consistent catalog display
            // - Ensure photoUrls is not null (empty list if missing) so downstream consumers can rely on the field
            // - Mark pet status as NOTIFIED so the workflow advances to the NOTIFIED state

            String status = entity.getStatus();
            if (status != null && "AVAILABLE".equalsIgnoreCase(status.trim())) {

                // Normalize species to lowercase trimmed value (consistent catalog keys)
                if (entity.getSpecies() != null) {
                    entity.setSpecies(entity.getSpecies().trim().toLowerCase());
                }

                // Normalize breed by trimming whitespace (keep case as-is)
                if (entity.getBreed() != null) {
                    entity.setBreed(entity.getBreed().trim());
                }

                // Ensure photoUrls is initialized to an empty list if absent
                if (entity.getPhotoUrls() == null) {
                    entity.setPhotoUrls(new ArrayList<>());
                }

                // Mark as notified so the workflow state reflects catalog notification
                entity.setStatus("NOTIFIED");

                logger.info("Pet {} marked as NOTIFIED for catalog push", entity.getId());
            } else {
                logger.info("Pet {} is not AVAILABLE (status={}) - skipping catalog notification", entity.getId(), status);
            }
        } catch (Exception ex) {
            // Log and continue; any thrown exception will be captured by serializer error handling if needed
            logger.error("Error while processing NotifyCatalogProcessor for pet {}: {}", entity != null ? entity.getId() : "null", ex.getMessage(), ex);
        }

        return entity;
    }
}