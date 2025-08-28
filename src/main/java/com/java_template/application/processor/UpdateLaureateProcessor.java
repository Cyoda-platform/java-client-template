package com.java_template.application.processor;

import com.java_template.application.entity.laureate.version_1.Laureate;
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
import java.util.UUID;

@Component
public class UpdateLaureateProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(UpdateLaureateProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public UpdateLaureateProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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

    private boolean isValidEntity(Laureate entity) {
        return entity != null && entity.isValid();
    }

    private Laureate processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Laureate> context) {
        Laureate incoming = context.entity();

        // Business goal:
        // - Find existing Laureate by source id (id)
        // - Merge incoming non-null data into existing record (incoming takes precedence)
        // - Update existing persisted Laureate via EntityService.updateItem(...)
        // - Update incoming entity's lastUpdatedAt to reflect the merge (it will be persisted by the workflow)
        // Notes:
        // - Do not perform operations on the triggering entity via EntityService (we only mutate its state)
        // - We are allowed to update the existing stored laureate (a different entity instance) via EntityService

        try {
            // Build simple search condition on the business id field
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.id", "EQUALS", incoming.getId() == null ? "" : incoming.getId().toString())
            );

            CompletableFuture<List<DataPayload>> future = entityService.getItemsByCondition(
                Laureate.ENTITY_NAME,
                Laureate.ENTITY_VERSION,
                condition,
                true
            );

            List<DataPayload> results = future.get();

            if (results != null && !results.isEmpty()) {
                // Pick the first existing match (assumes id unique)
                DataPayload existingPayload = results.get(0);

                // Attempt to extract technical id from payload metadata if present
                String technicalId = null;
                try {
                    Object maybeId = existingPayload.getId();
                    if (maybeId != null) {
                        technicalId = maybeId.toString();
                    }
                } catch (Throwable t) {
                    // fallback: maybe payload has technicalId field or no accessible id
                    logger.debug("Could not access payload id via getId(): {}", t.getMessage());
                }

                // Deserialize stored laureate
                Laureate stored = null;
                try {
                    stored = objectMapper.treeToValue(existingPayload.getData(), Laureate.class);
                } catch (Exception e) {
                    logger.error("Failed to deserialize existing Laureate payload: {}", e.getMessage(), e);
                }

                if (stored != null) {
                    // Merge logic: incoming (fresh) values overwrite stored when non-null/non-blank.
                    // For numeric values (ageAtAward) prefer incoming if non-null.
                    if (incoming.getFirstname() != null && !incoming.getFirstname().isBlank()) {
                        stored.setFirstname(incoming.getFirstname());
                    }
                    if (incoming.getSurname() != null && !incoming.getSurname().isBlank()) {
                        stored.setSurname(incoming.getSurname());
                    }
                    if (incoming.getBorn() != null && !incoming.getBorn().isBlank()) {
                        stored.setBorn(incoming.getBorn());
                    }
                    if (incoming.getDied() != null) {
                        stored.setDied(incoming.getDied());
                    }
                    if (incoming.getBornCity() != null && !incoming.getBornCity().isBlank()) {
                        stored.setBornCity(incoming.getBornCity());
                    }
                    if (incoming.getBornCountry() != null && !incoming.getBornCountry().isBlank()) {
                        stored.setBornCountry(incoming.getBornCountry());
                    }
                    if (incoming.getBornCountryCode() != null && !incoming.getBornCountryCode().isBlank()) {
                        stored.setBornCountryCode(incoming.getBornCountryCode());
                    }
                    if (incoming.getCategory() != null && !incoming.getCategory().isBlank()) {
                        stored.setCategory(incoming.getCategory());
                    }
                    if (incoming.getGender() != null && !incoming.getGender().isBlank()) {
                        stored.setGender(incoming.getGender());
                    }
                    if (incoming.getMotivation() != null && !incoming.getMotivation().isBlank()) {
                        stored.setMotivation(incoming.getMotivation());
                    }
                    if (incoming.getAffiliationName() != null && !incoming.getAffiliationName().isBlank()) {
                        stored.setAffiliationName(incoming.getAffiliationName());
                    }
                    if (incoming.getAffiliationCity() != null && !incoming.getAffiliationCity().isBlank()) {
                        stored.setAffiliationCity(incoming.getAffiliationCity());
                    }
                    if (incoming.getAffiliationCountry() != null && !incoming.getAffiliationCountry().isBlank()) {
                        stored.setAffiliationCountry(incoming.getAffiliationCountry());
                    }
                    if (incoming.getYear() != null && !incoming.getYear().isBlank()) {
                        stored.setYear(incoming.getYear());
                    }
                    if (incoming.getSourceSnapshot() != null && !incoming.getSourceSnapshot().isBlank()) {
                        stored.setSourceSnapshot(incoming.getSourceSnapshot());
                    }

                    if (incoming.getAgeAtAward() != null) {
                        stored.setAgeAtAward(incoming.getAgeAtAward());
                    }

                    if (incoming.getNormalizedCountryCode() != null && !incoming.getNormalizedCountryCode().isBlank()) {
                        stored.setNormalizedCountryCode(incoming.getNormalizedCountryCode());
                    }

                    // Update lastUpdatedAt to now for stored record
                    String now = Instant.now().toString();
                    stored.setLastUpdatedAt(now);

                    // Persist update to existing stored laureate if we can determine its technical id
                    if (technicalId != null && !technicalId.isBlank()) {
                        try {
                            UUID storedUuid = UUID.fromString(technicalId);
                            CompletableFuture<java.util.UUID> updateFuture = entityService.updateItem(storedUuid, stored);
                            updateFuture.get();
                            logger.info("Updated existing Laureate entity (technicalId={}) with merged data.", technicalId);
                        } catch (Exception e) {
                            logger.error("Failed to update existing Laureate (technicalId={}): {}", technicalId, e.getMessage(), e);
                        }
                    } else {
                        logger.warn("Could not determine technical id for existing Laureate - skipping entityService.updateItem. Stored record will not be updated via service.");
                    }

                    // Mutate incoming (triggering) entity to reflect merge result and updated timestamp.
                    // This entity will be persisted by the workflow engine automatically.
                    incoming.setFirstname(stored.getFirstname());
                    incoming.setSurname(stored.getSurname());
                    incoming.setBorn(stored.getBorn());
                    incoming.setDied(stored.getDied());
                    incoming.setBornCity(stored.getBornCity());
                    incoming.setBornCountry(stored.getBornCountry());
                    incoming.setBornCountryCode(stored.getBornCountryCode());
                    incoming.setCategory(stored.getCategory());
                    incoming.setGender(stored.getGender());
                    incoming.setMotivation(stored.getMotivation());
                    incoming.setAffiliationName(stored.getAffiliationName());
                    incoming.setAffiliationCity(stored.getAffiliationCity());
                    incoming.setAffiliationCountry(stored.getAffiliationCountry());
                    incoming.setAgeAtAward(stored.getAgeAtAward());
                    incoming.setNormalizedCountryCode(stored.getNormalizedCountryCode());
                    incoming.setSourceSnapshot(stored.getSourceSnapshot());
                    incoming.setLastUpdatedAt(stored.getLastUpdatedAt());
                    incoming.setYear(stored.getYear());
                } else {
                    logger.warn("Existing laureate found by condition but failed to deserialize stored object. No merge applied.");
                }
            } else {
                logger.info("No existing Laureate found for id={} - nothing to update. Leaving incoming entity as-is.", incoming.getId());
                // Set lastUpdatedAt for incoming entity
                incoming.setLastUpdatedAt(Instant.now().toString());
            }
        } catch (Exception ex) {
            logger.error("Error during UpdateLaureateProcessor processing: {}", ex.getMessage(), ex);
            // Ensure incoming entity has a lastUpdatedAt even on error
            try {
                incoming.setLastUpdatedAt(Instant.now().toString());
            } catch (Exception ignore) {}
        }

        return incoming;
    }
}