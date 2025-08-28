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

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.UUID;
import java.time.Instant;

@Component
public class DuplicateDetectorProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DuplicateDetectorProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public DuplicateDetectorProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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
        Laureate entity = context.entity();

        // Build search condition: find laureates with same source id
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.id", "EQUALS", String.valueOf(entity.getId()))
        );

        List<DataPayload> dataPayloads = null;
        try {
            dataPayloads = entityService.getItemsByCondition(
                    Laureate.ENTITY_NAME,
                    Laureate.ENTITY_VERSION,
                    condition,
                    true
            ).get();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while searching for existing laureates: {}", ie.getMessage(), ie);
        } catch (ExecutionException ee) {
            logger.error("Execution error while searching for existing laureates: {}", ee.getMessage(), ee);
        } catch (Exception e) {
            logger.error("Unexpected error while searching for existing laureates: {}", e.getMessage(), e);
        }

        if (dataPayloads == null || dataPayloads.isEmpty()) {
            // No existing entity found -> not a duplicate, allow persist path
            logger.debug("No existing laureate found for id={}, will be treated as new.", entity.getId());
            return entity;
        }

        // There are existing records with same id: treat as duplicates -> compare and update existing records if needed
        for (DataPayload payload : dataPayloads) {
            try {
                // Map payload data to Laureate instance
                Laureate existing = objectMapper.treeToValue(payload.getData(), Laureate.class);
                if (existing == null) continue;

                if (areDifferent(existing, entity)) {
                    // Copy relevant updatable fields from incoming entity to existing
                    copyUpdatableFields(entity, existing);
                    // Ensure lastUpdatedAt is set
                    existing.setLastUpdatedAt(entity.getLastUpdatedAt() != null ? entity.getLastUpdatedAt() : Instant.now().toString());

                    // Attempt to retrieve technical id from payload to perform update.
                    // DataPayload is expected to provide an id that can be parsed as UUID.
                    String technicalId = null;
                    try {
                        // Many payload implementations expose getId(); try to call it
                        technicalId = (String) payload.getClass().getMethod("getId").invoke(payload);
                    } catch (Exception ex) {
                        // Fallback: try getTechnicalId
                        try {
                            technicalId = (String) payload.getClass().getMethod("getTechnicalId").invoke(payload);
                        } catch (Exception ex2) {
                            logger.warn("Unable to extract technical id from DataPayload for existing laureate. Skipping update. Payload class: {}", payload.getClass().getName());
                        }
                    }

                    if (technicalId != null) {
                        try {
                            UUID existingUuid = UUID.fromString(technicalId);
                            entityService.updateItem(existingUuid, existing).get();
                            logger.info("Updated existing Laureate (technicalId={}) with id={}", technicalId, existing.getId());
                        } catch (IllegalArgumentException iae) {
                            logger.warn("Existing payload id is not a valid UUID ({}). Skipping update.", technicalId);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            logger.error("Interrupted while updating existing laureate: {}", ie.getMessage(), ie);
                        } catch (ExecutionException ee) {
                            logger.error("Execution error while updating existing laureate: {}", ee.getMessage(), ee);
                        } catch (Exception e) {
                            logger.error("Unexpected error while updating existing laureate: {}", e.getMessage(), e);
                        }
                    } else {
                        logger.warn("Technical id for existing laureate not found; cannot perform update.");
                    }
                } else {
                    logger.debug("Existing laureate (id={}) identical to incoming; no update required.", existing.getId());
                }
            } catch (Exception ex) {
                logger.error("Failed to process existing laureate payload for id={}: {}", entity.getId(), ex.getMessage(), ex);
            }
        }

        // Mark incoming entity as processed for deduplication step by updating lastUpdatedAt (helps downstream processors)
        entity.setLastUpdatedAt(Instant.now().toString());
        return entity;
    }

    private boolean areDifferent(Laureate existing, Laureate incoming) {
        if (!nullSafeEquals(existing.getFirstname(), incoming.getFirstname())) return true;
        if (!nullSafeEquals(existing.getSurname(), incoming.getSurname())) return true;
        if (!nullSafeEquals(existing.getYear(), incoming.getYear())) return true;
        if (!nullSafeEquals(existing.getCategory(), incoming.getCategory())) return true;
        if (!nullSafeEquals(existing.getMotivation(), incoming.getMotivation())) return true;
        if (!nullSafeEquals(existing.getAffiliationName(), incoming.getAffiliationName())) return true;
        if (!nullSafeEquals(existing.getAffiliationCity(), incoming.getAffiliationCity())) return true;
        if (!nullSafeEquals(existing.getAffiliationCountry(), incoming.getAffiliationCountry())) return true;
        if (!nullSafeEquals(existing.getBorn(), incoming.getBorn())) return true;
        if (!nullSafeEquals(existing.getDied(), incoming.getDied())) return true;
        if (!nullSafeEquals(existing.getBornCountry(), incoming.getBornCountry())) return true;
        if (!nullSafeEquals(existing.getBornCountryCode(), incoming.getBornCountryCode())) return true;
        if (!nullSafeEquals(existing.getNormalizedCountryCode(), incoming.getNormalizedCountryCode())) return true;
        if (!nullSafeEquals(existing.getSourceSnapshot(), incoming.getSourceSnapshot())) return true;
        if (!nullSafeEquals(existing.getGender(), incoming.getGender())) return true;
        if (!nullSafeEquals(existing.getAgeAtAward(), incoming.getAgeAtAward())) return true;
        return false;
    }

    private void copyUpdatableFields(Laureate from, Laureate to) {
        to.setFirstname(from.getFirstname());
        to.setSurname(from.getSurname());
        to.setYear(from.getYear());
        to.setCategory(from.getCategory());
        to.setMotivation(from.getMotivation());
        to.setAffiliationName(from.getAffiliationName());
        to.setAffiliationCity(from.getAffiliationCity());
        to.setAffiliationCountry(from.getAffiliationCountry());
        to.setBorn(from.getBorn());
        to.setDied(from.getDied());
        to.setBornCountry(from.getBornCountry());
        to.setBornCountryCode(from.getBornCountryCode());
        to.setNormalizedCountryCode(from.getNormalizedCountryCode());
        to.setSourceSnapshot(from.getSourceSnapshot());
        to.setGender(from.getGender());
        to.setAgeAtAward(from.getAgeAtAward());
    }

    private boolean nullSafeEquals(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }
}