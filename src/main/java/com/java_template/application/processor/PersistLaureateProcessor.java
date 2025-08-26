package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Processor responsible for final persistence and deduplication logic for Laureate entities.
 *
 * Notes:
 * - This processor MUST NOT call update/add/delete operations for the Laureate entity that triggered the workflow.
 * - It MAY read existing Laureate records to detect duplicates and MAY merge information from existing records
 *   into the current entity instance. Any changes to the current entity will be persisted automatically by Cyoda.
 */
@Component
public class PersistLaureateProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PersistLaureateProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public PersistLaureateProcessor(SerializerFactory serializerFactory,
                                    EntityService entityService,
                                    ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Laureate for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Laureate.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
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
        if (entity == null) {
            logger.warn("PersistLaureateProcessor invoked with null entity");
            return null;
        }

        try {
            // Deduplication strategy:
            // - Search for existing Laureate records having the same source id (id).
            // - Exclude the current technicalId to avoid matching itself (if present in datastore).
            // - If an existing record is found, merge useful non-null fields from the existing record
            //   into the current entity instance. Do not perform update against the existing entity.
            // - If no existing record found, simply allow the current entity to be persisted (Cyoda will persist it).
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.id", "EQUALS", String.valueOf(entity.getId()))
            );

            // Exclude current technicalId if present to avoid false-positive when searching persisted records.
            if (entity.getTechnicalId() != null && !entity.getTechnicalId().isBlank()) {
                Condition notSameTechId = Condition.of("$.technicalId", "NOT_EQUAL", entity.getTechnicalId());
                condition = SearchConditionRequest.group("AND",
                    Condition.of("$.id", "EQUALS", String.valueOf(entity.getId())),
                    notSameTechId
                );
            }

            CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                Laureate.ENTITY_NAME,
                String.valueOf(Laureate.ENTITY_VERSION),
                condition,
                true
            );

            ArrayNode results = itemsFuture.join();

            if (results != null && results.size() > 0) {
                // Duplicate(s) found - take first match as source of merge information
                JsonNode first = results.get(0);
                try {
                    Laureate existing = objectMapper.treeToValue(first, Laureate.class);
                    logger.info("Duplicate laureate detected (id={}), merging available fields into current entity. existingTechnicalId={}",
                        entity.getId(), existing != null ? existing.getTechnicalId() : null);

                    // Merge non-null fields from existing into current entity where current is missing.
                    if (existing != null) {
                        if ((entity.getAgeAtAward() == null) && (existing.getAgeAtAward() != null)) {
                            entity.setAgeAtAward(existing.getAgeAtAward());
                        }
                        if ((entity.getAffiliation() == null || entity.getAffiliation().getName() == null)
                            && existing.getAffiliation() != null) {
                            entity.setAffiliation(existing.getAffiliation());
                        }
                        if ((entity.getBornCountryCode() == null || entity.getBornCountryCode().isBlank())
                            && existing.getBornCountryCode() != null) {
                            entity.setBornCountryCode(existing.getBornCountryCode());
                        }
                        if ((entity.getBornCountry() == null || entity.getBornCountry().isBlank())
                            && existing.getBornCountry() != null) {
                            entity.setBornCountry(existing.getBornCountry());
                        }
                        if ((entity.getBornCity() == null || entity.getBornCity().isBlank())
                            && existing.getBornCity() != null) {
                            entity.setBornCity(existing.getBornCity());
                        }
                        if ((entity.getMotivation() == null || entity.getMotivation().isBlank())
                            && existing.getMotivation() != null) {
                            entity.setMotivation(existing.getMotivation());
                        }
                        if ((entity.getGender() == null || entity.getGender().isBlank())
                            && existing.getGender() != null) {
                            entity.setGender(existing.getGender());
                        }
                        if ((entity.getDied() == null || entity.getDied().isBlank())
                            && existing.getDied() != null) {
                            entity.setDied(existing.getDied());
                        }
                        // For duplicates, mark validation status as INVALID to indicate duplicate detection.
                        entity.setValidationStatus("INVALID");
                    } else {
                        // defensive: mark as invalid duplicate if we couldn't parse existing
                        entity.setValidationStatus("INVALID");
                    }
                } catch (Exception ex) {
                    logger.warn("Failed to parse existing laureate record for merging. Marking current record as INVALID duplicate. id={}", entity.getId(), ex);
                    entity.setValidationStatus("INVALID");
                }
            } else {
                // No duplicate found: mark entity as VALID for persistence and ensure final persistent-related fields are set.
                entity.setValidationStatus("VALID");
            }

        } catch (Exception ex) {
            // On any unexpected error during deduplication, mark the entity as INVALID and log the error.
            logger.error("Error while running PersistLaureateProcessor deduplication for laureate id={}. Marking as INVALID.", entity.getId(), ex);
            entity.setValidationStatus("INVALID");
        }

        // Return the (possibly modified) entity. Cyoda will persist the entity state automatically.
        return entity;
    }
}