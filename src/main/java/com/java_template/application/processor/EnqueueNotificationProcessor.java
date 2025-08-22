package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
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

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Component
public class EnqueueNotificationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EnqueueNotificationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public EnqueueNotificationProcessor(SerializerFactory serializerFactory,
                                        EntityService entityService,
                                        ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Subscriber for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Subscriber.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Subscriber entity) {
        return entity != null && entity.isValid();
    }

    private Subscriber processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Subscriber> context) {
        Subscriber subscriber = context.entity();
        if (subscriber == null) return subscriber;

        // Only enqueue notifications for active subscribers
        String status = subscriber.getStatus();
        if (status == null || !status.equalsIgnoreCase("active")) {
            logger.debug("Subscriber {} is not active (status={}), skipping enqueue", subscriber.getId(), status);
            return subscriber;
        }

        // Parse filters (if any) and build search condition for Laureate lookup
        String filters = subscriber.getFilters();
        SearchConditionRequest conditionRequest = null;
        try {
            if (filters != null && !filters.isBlank()) {
                JsonNode filtersNode = objectMapper.readTree(filters);
                if (filtersNode != null && filtersNode.isObject()) {
                    List<Condition> conditions = new ArrayList<>();
                    Iterator<Map.Entry<String, JsonNode>> fields = filtersNode.fields();
                    while (fields.hasNext()) {
                        Map.Entry<String, JsonNode> field = fields.next();
                        String fieldName = field.getKey();
                        JsonNode valueNode = field.getValue();
                        // Only support simple equality conditions for now
                        if (valueNode.isTextual()) {
                            conditions.add(Condition.of("$. " + fieldName, "EQUALS", valueNode.asText()));
                        } else if (valueNode.isInt() || valueNode.isLong()) {
                            conditions.add(Condition.of("$. " + fieldName, "EQUALS", String.valueOf(valueNode.asLong())));
                        } else if (valueNode.isNumber()) {
                            conditions.add(Condition.of("$. " + fieldName, "EQUALS", valueNode.asText()));
                        } else if (valueNode.isBoolean()) {
                            conditions.add(Condition.of("$. " + fieldName, "EQUALS", String.valueOf(valueNode.asBoolean())));
                        } else {
                            // ignore complex nodes
                        }
                    }
                    if (!conditions.isEmpty()) {
                        conditionRequest = SearchConditionRequest.group("AND", conditions.toArray(new Condition[0]));
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to parse subscriber.filters for subscriber {}: {}", subscriber.getId(), e.getMessage());
        }

        // Fetch matching laureates (if any)
        int matchedCount = 0;
        try {
            ArrayNode items;
            if (conditionRequest != null) {
                items = entityService.getItemsByCondition(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION),
                    conditionRequest,
                    true
                ).get();
            } else {
                // No filters - fetch all laureates (may be large; but honoring requirement to support simple dispatch)
                items = entityService.getItems(
                    Laureate.ENTITY_NAME,
                    String.valueOf(Laureate.ENTITY_VERSION)
                ).get();
            }
            if (items != null) {
                matchedCount = items.size();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while fetching laureates for subscriber {}: {}", subscriber.getId(), ie.getMessage());
        } catch (ExecutionException ee) {
            logger.error("Error fetching laureates for subscriber {}: {}", subscriber.getId(), ee.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error fetching laureates for subscriber {}: {}", subscriber.getId(), e.getMessage());
        }

        // Update subscriber.filters metadata with last enqueued timestamp and count
        try {
            ObjectNode updatedFiltersNode;
            if (filters != null && !filters.isBlank()) {
                JsonNode existing = objectMapper.readTree(filters);
                if (existing != null && existing.isObject()) {
                    updatedFiltersNode = (ObjectNode) existing;
                } else {
                    updatedFiltersNode = objectMapper.createObjectNode();
                }
            } else {
                updatedFiltersNode = objectMapper.createObjectNode();
            }
            String now = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
            updatedFiltersNode.put("lastEnqueuedTimestamp", now);
            updatedFiltersNode.put("lastEnqueuedCount", matchedCount);
            // do not overwrite original filters semantics; just augment with metadata keys
            subscriber.setFilters(objectMapper.writeValueAsString(updatedFiltersNode));
        } catch (Exception e) {
            logger.warn("Failed to update subscriber.filters metadata for subscriber {}: {}", subscriber.getId(), e.getMessage());
        }

        // Preference specific handling (only metadata update here; actual dispatch handled by downstream workers)
        String preference = subscriber.getPreference();
        if (preference != null && preference.equalsIgnoreCase("immediate") && matchedCount > 0) {
            logger.info("Subscriber {} (immediate) has {} matching laureates — enqueued notifications (logical)", subscriber.getId(), matchedCount);
            // Actual enqueue to a delivery queue or notification entity is not implemented here;
            // We persist metadata into subscriber.filters so downstream processors can act upon it.
        } else {
            logger.debug("Subscriber {} preference={} matchedCount={}", subscriber.getId(), preference, matchedCount);
        }

        return subscriber;
    }
}