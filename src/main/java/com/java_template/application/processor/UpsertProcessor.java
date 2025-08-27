package com.java_template.application.processor;

import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class UpsertProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(UpsertProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public UpsertProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Laureate for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Laureate.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Laureate entity) {
        return entity != null && entity.isValid();
    }

    private Laureate processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Laureate> context) {
        Laureate entity = context.entity();
        if (entity == null) return null;

        try {
            // Ensure lastSeenAt is updated for this ingestion
            entity.setLastSeenAt(Instant.now().toString());

            // Build search condition to find existing laureate by source id
            SearchConditionRequest condition = SearchConditionRequest.group(
                "AND",
                Condition.of("$.id", "EQUALS", String.valueOf(entity.getId()))
            );

            CompletableFuture<List<DataPayload>> filteredItemsFuture = entityService.getItemsByCondition(
                Laureate.ENTITY_NAME,
                Laureate.ENTITY_VERSION,
                condition,
                true
            );

            List<DataPayload> dataPayloads = filteredItemsFuture.get();

            if (dataPayloads == null || dataPayloads.isEmpty()) {
                // No existing record found -> new record.
                // Per system rules: do not call entityService.addItem for the entity that triggered the workflow.
                // The current entity will be persisted automatically by Cyoda.
                logger.info("No existing Laureate found for source id={}, marking as NEW (will be persisted by workflow)", entity.getId());
                // Optionally set a normalization on the entity (no new fields available)
                if (entity.getValidationStatus() == null || entity.getValidationStatus().isBlank()) {
                    entity.setValidationStatus("VALID");
                }
            } else {
                // Existing record(s) found -> perform an update on the stored entity.
                // We will merge incoming data into the stored entity and update it via EntityService.
                // Note: updating other stored entities via EntityService is allowed.
                DataPayload existingPayload = dataPayloads.get(0);
                Laureate existing = objectMapper.treeToValue(existingPayload.getData(), Laureate.class);

                // Merge: prefer incoming (entity) non-null values, fall back to existing
                if (entity.getFirstname() != null) existing.setFirstname(entity.getFirstname());
                if (entity.getSurname() != null) existing.setSurname(entity.getSurname());
                if (entity.getBorn() != null) existing.setBorn(entity.getBorn());
                if (entity.getDied() != null) existing.setDied(entity.getDied());
                if (entity.getBornCountry() != null) existing.setBornCountry(entity.getBornCountry());
                if (entity.getBornCountryCode() != null) existing.setBornCountryCode(entity.getBornCountryCode());
                if (entity.getBornCity() != null) existing.setBornCity(entity.getBornCity());
                if (entity.getCategory() != null) existing.setCategory(entity.getCategory());
                if (entity.getYear() != null) existing.setYear(entity.getYear());
                if (entity.getMotivation() != null) existing.setMotivation(entity.getMotivation());
                if (entity.getAffiliationName() != null) existing.setAffiliationName(entity.getAffiliationName());
                if (entity.getAffiliationCity() != null) existing.setAffiliationCity(entity.getAffiliationCity());
                if (entity.getAffiliationCountry() != null) existing.setAffiliationCountry(entity.getAffiliationCountry());
                if (entity.getGender() != null) existing.setGender(entity.getGender());
                if (entity.getAgeAtAward() != null) existing.setAgeAtAward(entity.getAgeAtAward());
                if (entity.getNormalizedCountryCode() != null) existing.setNormalizedCountryCode(entity.getNormalizedCountryCode());

                // Always update lastSeenAt on stored entity
                existing.setLastSeenAt(Instant.now().toString());

                // Preserve or update validationStatus if present on incoming entity
                if (entity.getValidationStatus() != null && !entity.getValidationStatus().isBlank()) {
                    existing.setValidationStatus(entity.getValidationStatus());
                }

                // Attempt to determine technical id for the existing payload.
                // DataPayload typically contains metadata with the technical id; try common accessors.
                String technicalIdStr = null;
                try {
                    // Try common patterns for metadata id extraction
                    if (existingPayload.getMetadata() != null && existingPayload.getMetadata().getId() != null) {
                        technicalIdStr = existingPayload.getMetadata().getId();
                    } else if (existingPayload.getId() != null) {
                        technicalIdStr = existingPayload.getId();
                    }
                } catch (Exception ex) {
                    // ignore - will attempt to continue if null
                }

                if (technicalIdStr != null && !technicalIdStr.isBlank()) {
                    try {
                        CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                            UUID.fromString(technicalIdStr),
                            existing
                        );
                        UUID updatedId = updatedIdFuture.get();
                        logger.info("Updated existing Laureate (technicalId={}) for source id={}", updatedId, entity.getId());
                    } catch (Exception e) {
                        logger.error("Failed to update existing Laureate for source id={}: {}", entity.getId(), e.getMessage(), e);
                    }
                } else {
                    // Fallback: unable to determine technical id - log and skip update to avoid accidental adds of same entity
                    logger.warn("Could not determine technical id for existing Laureate payload; skipping update for source id={}", entity.getId());
                }

                // Mark incoming entity's validation status to reflect it matched an existing record.
                entity.setValidationStatus(entity.getValidationStatus() != null && !entity.getValidationStatus().isBlank()
                    ? entity.getValidationStatus()
                    : "VALID");
            }
        } catch (Exception e) {
            logger.error("Error during upsert processing for Laureate id={}: {}", entity.getId(), e.getMessage(), e);
            // Do not throw; mark entity as invalid outcome if necessary
            String prev = entity.getValidationStatus();
            entity.setValidationStatus("INVALID:upsert_error" + (prev != null ? ";" + prev : ""));
        }

        return entity;
    }
}