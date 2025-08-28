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

import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class PetValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PetValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public PetValidationProcessor(SerializerFactory serializerFactory,
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

        // Business logic implementation based on functional requirements:
        // - Detect duplicates (by external id OR by name+breed+location fuzzy match)
        // - If duplicate found, mark status = "REMOVED"
        // - Preserve the current entity as the triggering entity (do not add/update/delete it via EntityService)
        // - Log important decisions for observability

        try {
            boolean duplicateFound = false;

            // 1) Check duplicate by external source id if provided
            if (entity.getId() != null && !entity.getId().isBlank()) {
                SearchConditionRequest idCondition = SearchConditionRequest.group("AND",
                        Condition.of("$.id", "EQUALS", entity.getId())
                );
                CompletableFuture<List<DataPayload>> idFuture = entityService.getItemsByCondition(
                        Pet.ENTITY_NAME, Pet.ENTITY_VERSION, idCondition, true
                );
                List<DataPayload> idResults = idFuture.get();
                if (idResults != null && !idResults.isEmpty()) {
                    duplicateFound = true;
                    logger.info("Duplicate pet found by external id [{}] for technical entity: {}", entity.getId(), entity.getTechnicalId());
                }
            }

            // 2) If no id-duplicate, attempt fuzzy match by name+breed+location (case-insensitive IEQUALS)
            if (!duplicateFound) {
                String name = entity.getName();
                String breed = entity.getBreed();
                String location = entity.getLocation();
                if (name != null && !name.isBlank()
                        && breed != null && !breed.isBlank()
                        && location != null && !location.isBlank()) {

                    SearchConditionRequest fuzzyCondition = SearchConditionRequest.group("AND",
                            Condition.of("$.name", "IEQUALS", name),
                            Condition.of("$.breed", "IEQUALS", breed),
                            Condition.of("$.location", "IEQUALS", location)
                    );

                    CompletableFuture<List<DataPayload>> fuzzyFuture = entityService.getItemsByCondition(
                            Pet.ENTITY_NAME, Pet.ENTITY_VERSION, fuzzyCondition, true
                    );
                    List<DataPayload> fuzzyResults = fuzzyFuture.get();
                    if (fuzzyResults != null && !fuzzyResults.isEmpty()) {
                        duplicateFound = true;
                        logger.info("Duplicate pet found by name+breed+location for pet [{}|{}|{}]", name, breed, location);
                    }
                }
            }

            // If duplicate found, mark REMOVED
            if (duplicateFound) {
                entity.setStatus("REMOVED");
                logger.info("Pet marked as REMOVED due to duplicate detection. technicalId: {}", entity.getTechnicalId());
                return entity;
            }

            // Additional validation/mapping rules:
            // If source provided, ensure basic mapping expectations (no heavy enrichment here)
            if (entity.getSource() != null && !entity.getSource().isBlank()) {
                // Example: if PetstoreAPI source but no photos provided, leave as-is (enrichment step will fetch photos)
                logger.debug("Pet source present: {} for technicalId: {}", entity.getSource(), entity.getTechnicalId());
            }

            // Default behavior: do not change entity.status here except for duplicates.
            // If business required setting an initial published status, that should be handled by PublishPetProcessor.

        } catch (Exception ex) {
            logger.error("Error during Pet validation processing: {}", ex.getMessage(), ex);
            // In case of unexpected errors, mark entity status to REMOVED to avoid bad data propagation.
            if (entity != null) {
                entity.setStatus("REMOVED");
            }
        }

        return entity;
    }
}