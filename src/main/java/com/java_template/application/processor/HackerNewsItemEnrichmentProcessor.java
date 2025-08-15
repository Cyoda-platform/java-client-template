package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.hackernewsitem.version_1.HackerNewsItem;
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

import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
public class HackerNewsItemEnrichmentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(HackerNewsItemEnrichmentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final ObjectMapper mapper;
    private final EntityService entityService;

    public HackerNewsItemEnrichmentProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper mapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.mapper = mapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing HackerNewsItem enrichment for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(HackerNewsItem.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(HackerNewsItem entity) {
        return entity != null && entity.getOriginalJson() != null && !entity.getOriginalJson().trim().isEmpty();
    }

    private HackerNewsItem processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<HackerNewsItem> context) {
        HackerNewsItem entity = context.entity();
        try {
            JsonNode node = mapper.readTree(entity.getOriginalJson());
            if (node.has("id") && node.get("id").canConvertToLong()) {
                entity.setId(node.get("id").longValue());
            }
            if (node.has("type") && !node.get("type").isNull()) {
                entity.setType(node.get("type").asText());
            }
            // Extract URL domain as source and set tags if URL present
            if (node.has("url") && !node.get("url").isNull()) {
                String url = node.get("url").asText();
                try {
                    URI uri = new URI(url);
                    String host = uri.getHost();
                    if (host != null) {
                        entity.setSource(host.startsWith("www.") ? host.substring(4) : host);
                        // add a simple tag indicating presence of URL
                        if (!entity.getTags().contains("has-url")) entity.getTags().add("has-url");
                    }
                } catch (Exception e) {
                    logger.debug("Failed to parse URL for enrichment: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to parse originalJson during enrichment: {}", e.getMessage());
            // leave fields as-is; state assigner will mark as invalid if necessary
        }
        entity.setImportTimestamp(Instant.now());
        entity.setUpdatedAt(Instant.now());

        // Persist the enriched entity if it exists in datastore (find by originalJson)
        try {
            ArrayNode results = entityService.getItemsByCondition(
                HackerNewsItem.ENTITY_NAME,
                String.valueOf(HackerNewsItem.ENTITY_VERSION),
                SearchConditionRequest.group("AND", Condition.of("$.originalJson", "EQUALS", entity.getOriginalJson())),
                true
            ).get(10, TimeUnit.SECONDS);

            if (results != null && results.size() > 0) {
                ObjectNode stored = (ObjectNode) results.get(0);
                if (stored.has("technicalId") && !stored.get("technicalId").isNull()) {
                    UUID technicalId = UUID.fromString(stored.get("technicalId").asText());
                    CompletableFuture<UUID> updated = entityService.updateItem(
                        HackerNewsItem.ENTITY_NAME,
                        String.valueOf(HackerNewsItem.ENTITY_VERSION),
                        technicalId,
                        entity
                    );
                    updated.get(10, TimeUnit.SECONDS);
                    logger.info("Enriched HackerNewsItem persisted (technicalId={})", technicalId);
                } else {
                    // Fallback: try to add as new item
                    CompletableFuture<UUID> added = entityService.addItem(
                        HackerNewsItem.ENTITY_NAME,
                        String.valueOf(HackerNewsItem.ENTITY_VERSION),
                        entity
                    );
                    UUID newId = added.get(10, TimeUnit.SECONDS);
                    logger.info("Enriched HackerNewsItem added to datastore (technicalId={})", newId);
                }
            } else {
                // No existing item found; create new
                CompletableFuture<UUID> added = entityService.addItem(
                    HackerNewsItem.ENTITY_NAME,
                    String.valueOf(HackerNewsItem.ENTITY_VERSION),
                    entity
                );
                UUID newId = added.get(10, TimeUnit.SECONDS);
                logger.info("Enriched HackerNewsItem added to datastore (technicalId={})", newId);
            }
        } catch (Exception e) {
            logger.warn("Failed to persist enriched HackerNewsItem: {}", e.getMessage());
            // don't fail processing; persist will be retried by surrounding workflow if needed
        }

        return entity;
    }
}
