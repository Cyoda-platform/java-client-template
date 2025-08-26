package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.UUID;

@Component
public class PersistLaureateProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PersistLaureateProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

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
        if (entity == null) return null;

        try {
            // Build a search condition to find existing laureates by business id (id)
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.id", "EQUALS", entity.getId() == null ? "" : String.valueOf(entity.getId()))
            );

            CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                Laureate.ENTITY_NAME,
                String.valueOf(Laureate.ENTITY_VERSION),
                condition,
                true
            );

            ArrayNode foundItems = itemsFuture.get();
            if (foundItems == null || foundItems.size() == 0) {
                // NEW record: set createdAt and persist as a new Laureate entity
                String now = isoNow();
                entity.setCreatedAt(now);

                try {
                    CompletableFuture<UUID> idFuture = entityService.addItem(
                        Laureate.ENTITY_NAME,
                        String.valueOf(Laureate.ENTITY_VERSION),
                        entity
                    );
                    UUID technicalId = idFuture.get();
                    logger.info("Added new Laureate (businessId={}), technicalId={}", entity.getId(), technicalId);
                } catch (InterruptedException | ExecutionException ex) {
                    logger.error("Failed to add new Laureate businessId={} : {}", entity.getId(), ex.getMessage(), ex);
                    // propagate error message into lastError-like field if available (not present on Laureate),
                    // but we keep entity state as-is so Cyoda workflow can mark failure if necessary.
                }

                return entity;
            } else {
                // Found existing records - attempt to decide UPDATED vs DUPLICATE
                // Pick first matched item for update/compare
                JsonNode storedNode = foundItems.get(0);
                // Attempt to locate the stored data node (some responses wrap entity data)
                JsonNode storedData = storedNode.has("data") ? storedNode.get("data") : storedNode;

                boolean differs = laureateDiffersFromStored(entity, storedData);

                // Retrieve technicalId for update if available
                String technicalIdStr = null;
                if (storedNode.has("technicalId")) {
                    technicalIdStr = storedNode.get("technicalId").asText(null);
                } else if (storedData.has("technicalId")) {
                    technicalIdStr = storedData.get("technicalId").asText(null);
                } else if (storedNode.has("id") && storedNode.get("id").isTextual()) {
                    technicalIdStr = storedNode.get("id").asText(null);
                }

                if (!differs) {
                    // Duplicate: nothing to persist. We can update context entity metadata if needed.
                    logger.info("Duplicate Laureate detected (businessId={}), skipping persistence", entity.getId());
                    // Optionally set createdAt from stored record if present
                    if (storedData.has("createdAt") && (entity.getCreatedAt() == null || entity.getCreatedAt().isBlank())) {
                        entity.setCreatedAt(storedData.get("createdAt").asText());
                    }
                    return entity;
                } else {
                    // UPDATED: merge semantics - here we send the incoming entity as the new state for the stored item
                    if (technicalIdStr != null) {
                        try {
                            UUID technicalId = UUID.fromString(technicalIdStr);
                            CompletableFuture<UUID> updated = entityService.updateItem(
                                Laureate.ENTITY_NAME,
                                String.valueOf(Laureate.ENTITY_VERSION),
                                technicalId,
                                entity
                            );
                            UUID updatedId = updated.get();
                            logger.info("Updated Laureate businessId={} technicalId={}", entity.getId(), updatedId);
                        } catch (IllegalArgumentException iae) {
                            logger.warn("Stored technicalId for laureate is not a valid UUID: {}. Skipping update.", technicalIdStr);
                        } catch (InterruptedException | ExecutionException ex) {
                            logger.error("Failed to update Laureate businessId={} : {}", entity.getId(), ex.getMessage(), ex);
                        }
                    } else {
                        // No technical id available — fallback to adding as new record
                        logger.warn("No technicalId available for existing laureate businessId={}. Adding as new record.", entity.getId());
                        entity.setCreatedAt(isoNow());
                        try {
                            CompletableFuture<UUID> idFuture = entityService.addItem(
                                Laureate.ENTITY_NAME,
                                String.valueOf(Laureate.ENTITY_VERSION),
                                entity
                            );
                            UUID technicalId = idFuture.get();
                            logger.info("Added Laureate as new record (fallback) businessId={} technicalId={}", entity.getId(), technicalId);
                        } catch (InterruptedException | ExecutionException ex) {
                            logger.error("Failed to add Laureate fallback businessId={} : {}", entity.getId(), ex.getMessage(), ex);
                        }
                    }

                    return entity;
                }
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error while accessing persistence for Laureate businessId={}: {}", entity.getId(), e.getMessage(), e);
            return entity;
        } catch (Exception ex) {
            logger.error("Unexpected error in PersistLaureateProcessor for businessId={}: {}", entity.getId(), ex.getMessage(), ex);
            return entity;
        }
    }

    private boolean laureateDiffersFromStored(Laureate incoming, JsonNode stored) {
        if (stored == null || incoming == null) return true;

        // helper to compare string fields (null-safe)
        java.util.function.BiPredicate<String, JsonNode> strDiff = (inc, node) -> {
            String storedVal = node != null && !node.isNull() ? node.asText(null) : null;
            if (inc == null && storedVal == null) return false;
            if (inc == null) return storedVal != null;
            return !inc.equals(storedVal);
        };

        // Compare important fields: firstname, surname, year, category, motivation, affiliationName, affiliationCity, affiliationCountry, born, died, bornCountry, bornCountryCode, gender, age
        if (strDiff.test(incoming.getFirstname(), stored.get("firstname"))) return true;
        if (strDiff.test(incoming.getSurname(), stored.get("surname"))) return true;
        if (strDiff.test(incoming.getYear(), stored.get("year"))) return true;
        if (strDiff.test(incoming.getCategory(), stored.get("category"))) return true;
        if (strDiff.test(incoming.getMotivation(), stored.get("motivation"))) return true;
        if (strDiff.test(incoming.getAffiliationName(), stored.get("affiliationName"))) return true;
        if (strDiff.test(incoming.getAffiliationCity(), stored.get("affiliationCity"))) return true;
        if (strDiff.test(incoming.getAffiliationCountry(), stored.get("affiliationCountry"))) return true;
        if (strDiff.test(incoming.getBorn(), stored.get("born"))) return true;
        if (strDiff.test(incoming.getDied(), stored.get("died"))) return true;
        if (strDiff.test(incoming.getBornCountry(), stored.get("bornCountry"))) return true;
        if (strDiff.test(incoming.getBornCountryCode(), stored.get("bornCountryCode"))) return true;
        if (strDiff.test(incoming.getGender(), stored.get("gender"))) return true;

        // age comparison (Integer)
        if (incoming.getAge() != null) {
            JsonNode storedAgeNode = stored.get("age");
            Integer storedAge = (storedAgeNode != null && storedAgeNode.isInt()) ? storedAgeNode.asInt() : null;
            if (!incoming.getAge().equals(storedAge)) return true;
        } else {
            // incoming age null vs stored non-null -> difference
            if (stored.has("age") && !stored.get("age").isNull()) return true;
        }

        // id comparison (Integer)
        if (incoming.getId() != null) {
            JsonNode storedIdNode = stored.get("id");
            Integer storedId = (storedIdNode != null && storedIdNode.isInt()) ? storedIdNode.asInt() : null;
            if (!incoming.getId().equals(storedId)) return true;
        } else {
            if (stored.has("id") && !stored.get("id").isNull()) return true;
        }

        // No differences found
        return false;
    }

    private String isoNow() {
        return Instant.now().toString();
    }
}