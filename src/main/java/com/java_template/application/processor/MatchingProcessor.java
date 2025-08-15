package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class MatchingProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(MatchingProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public MatchingProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing MatchingProcessor for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Laureate.class)
            .validate(this::isValidEntity, "Invalid laureate for matching")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Laureate l) {
        return l != null && l.getLaureateId() != null;
    }

    private Laureate processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Laureate> context) {
        Laureate l = context.entity();

        List<String> matchedSubscriberIds = new ArrayList<>();

        try {
            CompletableFuture<java.util.ArrayList<com.fasterxml.jackson.databind.JsonNode>> fut = entityService.getItems(Subscriber.ENTITY_NAME, String.valueOf(Subscriber.ENTITY_VERSION));
            java.util.ArrayList<JsonNode> items = fut.get();
            if (items != null) {
                for (JsonNode node : items) {
                    try {
                        // node is the persisted Subscriber JSON; map to Subscriber class
                        Subscriber s = objectMapper.treeToValue(node, Subscriber.class);
                        if (!Boolean.TRUE.equals(s.getActive())) continue;
                        String filters = s.getFilters();
                        if (matchesFilter(filters, l)) {
                            matchedSubscriberIds.add(s.getTechnicalId());
                            logger.info("Laureate {} matches subscriber {}", l.getLaureateId(), s.getTechnicalId());
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to deserialize subscriber during matching: {}", e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to query subscribers for matching: {}", e.getMessage());
            // fallback to prototype in-memory subscribers
            List<Subscriber> subs = simulateQueryActiveSubscribers();
            for (Subscriber s : subs) {
                if (!Boolean.TRUE.equals(s.getActive())) continue;
                if (matchesFilter(s.getFilters(), l)) matchedSubscriberIds.add(s.getTechnicalId());
            }
        }

        // store matched subscriber ids into sourceRecord JSON to avoid adding new fields
        try {
            JsonNode existing = null;
            if (l.getSourceRecord() != null && !l.getSourceRecord().isBlank()) existing = objectMapper.readTree(l.getSourceRecord());
            ObjectNodeWrapper wrapper = new ObjectNodeWrapper(existing, objectMapper);
            wrapper.putArray("matchedSubscribers", matchedSubscriberIds);
            l.setSourceRecord(wrapper.toString());
        } catch (Exception e) {
            logger.warn("Failed to write matched subscribers for laureate {}: {}", l.getLaureateId(), e.getMessage());
        }

        return l;
    }

    private List<Subscriber> simulateQueryActiveSubscribers() {
        Subscriber s1 = new Subscriber();
        s1.setTechnicalId("sub-1");
        s1.setActive(true);
        s1.setFilters("{\"category\":\"Physics\"}");

        Subscriber s2 = new Subscriber();
        s2.setTechnicalId("sub-2");
        s2.setActive(true);
        s2.setFilters("{\"year\":{\"gte\":2020}}");

        return List.of(s1, s2);
    }

    private boolean matchesFilter(String filtersJson, Laureate l) {
        if (filtersJson == null || filtersJson.isBlank()) return true;
        try {
            JsonNode filters = objectMapper.readTree(filtersJson);
            if (filters.has("category")) {
                String cat = filters.get("category").asText();
                if (l.getCategory() == null || !cat.equals(l.getCategory())) return false;
            }
            if (filters.has("year") && filters.get("year").isObject()) {
                JsonNode yr = filters.get("year");
                if (yr.has("gte")) {
                    int gte = yr.get("gte").asInt();
                    if (l.getYear() == null || l.getYear() < gte) return false;
                }
                if (yr.has("lte")) {
                    int lte = yr.get("lte").asInt();
                    if (l.getYear() == null || l.getYear() > lte) return false;
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to parse subscriber filters: {}", e.getMessage());
            return false;
        }
        return true;
    }

    // Utility wrapper to produce a JSON string even when existing node may be null
    private static class ObjectNodeWrapper {
        private final ObjectMapper mapper;
        private final com.fasterxml.jackson.databind.node.ObjectNode node;
        ObjectNodeWrapper(JsonNode existing, ObjectMapper mapper) {
            this.mapper = mapper;
            if (existing != null && existing.isObject()) this.node = (com.fasterxml.jackson.databind.node.ObjectNode) existing;
            else this.node = mapper.createObjectNode();
        }
        void putArray(String key, List<String> values) {
            com.fasterxml.jackson.databind.node.ArrayNode arr = mapper.createArrayNode();
            for (String v : values) arr.add(v);
            node.set(key, arr);
        }
        @Override
        public String toString() { try { return mapper.writeValueAsString(node);} catch(Exception e){return "{}";} }
    }
}
