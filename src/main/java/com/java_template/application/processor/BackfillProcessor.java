package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class BackfillProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(BackfillProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper;
    private final EntityService entityService;

    public BackfillProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing BackfillProcessor for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Subscriber.class)
            .validate(this::isValidEntity, "Invalid subscriber for backfill")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Subscriber s) {
        return s != null && Boolean.TRUE.equals(s.getActive());
    }

    private Subscriber processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Subscriber> context) {
        Subscriber subscriber = context.entity();

        // Parse backfillFromDate
        LocalDate from = null;
        try {
            if (subscriber.getBackfillFromDate() != null) {
                from = LocalDate.parse(subscriber.getBackfillFromDate());
            }
        } catch (Exception e) {
            logger.warn("Invalid backfillFromDate for subscriber {}: {}", subscriber.getTechnicalId(), subscriber.getBackfillFromDate());
        }

        // Query laureates since backfillFromDate using entityService; this is paged (simplified for prototype)
        int matches = 0;
        try {
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.createdAt", "GREATER_THAN", from == null ? "1970-01-01" : from.toString())
            );
            CompletableFuture<ArrayNode> future = entityService.getItemsByCondition(Laureate.ENTITY_NAME, String.valueOf(Laureate.ENTITY_VERSION), condition, true);
            ArrayNode items = future.get();
            if (items != null) {
                for (JsonNode n : items) {
                    // evaluate structured filters - subscriber.filters is a JSON string
                    boolean matched = true;
                    if (subscriber.getFilters() != null && !subscriber.getFilters().isBlank()) {
                        try {
                            JsonNode filters = objectMapper.readTree(subscriber.getFilters());
                            // very simple evaluation: if filters contains category equality
                            if (filters.has("category")) {
                                String cat = filters.get("category").asText();
                                if (!n.has("category") || !cat.equals(n.get("category").asText())) matched = false;
                            }
                        } catch (Exception e) {
                            logger.warn("Unable to parse filters for subscriber {}: {}", subscriber.getTechnicalId(), e.getMessage());
                        }
                    }
                    if (matched) matches++;
                }
            }
        } catch (Exception e) {
            logger.warn("Backfill query failed for subscriber {}: {}", subscriber.getTechnicalId(), e.getMessage());
        }

        // store a simple JSON summary into notificationHistory (string field on Subscriber)
        ObjectNode summary = objectMapper.createObjectNode();
        summary.put("backfillMatches", matches);
        if (from != null) summary.put("from", from.toString());
        summary.put("generatedAt", java.time.Instant.now().toString());
        try {
            subscriber.setNotificationHistory(objectMapper.writeValueAsString(summary));
        } catch (Exception e) {
            subscriber.setNotificationHistory("{\"backfillMatches\":0}");
        }

        logger.info("Subscriber {} backfill found {} matches (from={})", subscriber.getTechnicalId(), matches, from);
        return subscriber;
    }
}
