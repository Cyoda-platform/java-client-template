package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.inventoryitem.version_1.InventoryItem;
import com.java_template.application.entity.inventoryreportjob.version_1.InventoryReportJob;
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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
public class FetchInventoryProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FetchInventoryProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // External API endpoint from requirements
    private static final String SWAGGERHUB_SEARCH_URL = "https://api.swaggerhub.com/apis/CGIANNAROS/Test/1.0.0/developers/searchInventory";

    public FetchInventoryProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing FetchInventory for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(InventoryReportJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(InventoryReportJob entity) {
        return entity != null && entity.isValid();
    }

    private InventoryReportJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<InventoryReportJob> context) {
        InventoryReportJob job = context.entity();
        try {
            // Build a simple search condition from filters: only support category and location and sourceId
            SearchConditionRequest condition = null;
            JsonNode filters = job.getFilters();
            if (filters != null && filters.has("category")) {
                condition = SearchConditionRequest.group("AND",
                    Condition.of("$.category", "EQUALS", filters.get("category").asText())
                );
            } else if (filters != null && filters.has("location")) {
                condition = SearchConditionRequest.group("AND",
                    Condition.of("$.location", "EQUALS", filters.get("location").asText())
                );
            }

            // First attempt to call external SwaggerHub search to get freshest data and persist items
            List<InventoryItem> externalItems = fetchFromExternalApi(filters);
            if (externalItems != null && !externalItems.isEmpty()) {
                logger.info("Fetched {} items from external API for job {}", externalItems.size(), job.getTechnicalId());
                // Persist fetched items (allowed to add other entities)
                for (InventoryItem item : externalItems) {
                    try {
                        CompletableFuture<java.util.UUID> fut = entityService.addItem(
                            InventoryItem.ENTITY_NAME,
                            String.valueOf(InventoryItem.ENTITY_VERSION),
                            item
                        );
                        java.util.UUID id = fut.get();
                        if (id != null) item.setTechnicalId(id.toString());
                    } catch (Exception e) {
                        logger.warn("Failed to persist external item (sku={}): {}", item.getSku(), e.getMessage());
                    }
                }
            }

            CompletableFuture<ArrayNode> itemsFuture;
            if (condition != null) {
                itemsFuture = entityService.getItemsByCondition(
                    InventoryItem.ENTITY_NAME,
                    String.valueOf(InventoryItem.ENTITY_VERSION),
                    condition,
                    true
                );
            } else {
                itemsFuture = entityService.getItems(
                    InventoryItem.ENTITY_NAME,
                    String.valueOf(InventoryItem.ENTITY_VERSION)
                );
            }

            ArrayNode items = itemsFuture.get();
            if (items == null || items.size() == 0) {
                // No data - move to EXECUTING (downstream processors will create EMPTY report)
                job.setStatus("EXECUTING");
                return job;
            }

            // We do not persist items into the job (job has no metadata field). Downstream processors will re-query the entity store.
            job.setStatus("AGGREGATING");
        } catch (Exception e) {
            logger.error("Error fetching inventory for job {}: {}", job.getTechnicalId(), e.getMessage(), e);
            job.setStatus("FAILED");
        }
        return job;
    }

    private List<InventoryItem> fetchFromExternalApi(JsonNode filters) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            // Build query params
            StringBuilder sb = new StringBuilder(SWAGGERHUB_SEARCH_URL);
            boolean first = !SWAGGERHUB_SEARCH_URL.contains("?");
            if (filters != null) {
                if (filters.has("category")) {
                    sb.append(first ? "?" : "&");
                    sb.append("category=").append(urlEncode(filters.get("category").asText()));
                    first = false;
                }
                if (filters.has("location")) {
                    sb.append(first ? "?" : "&");
                    sb.append("location=").append(urlEncode(filters.get("location").asText()));
                    first = false;
                }
            }

            String url = sb.toString();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

            // Retry with simple backoff
            int attempts = 0;
            while (attempts < 3) {
                attempts++;
                try {
                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        String body = response.body();
                        JsonNode root = objectMapper.readTree(body);
                        // Expecting either array or object with items field
                        ArrayNode nodes = null;
                        if (root.isArray()) nodes = (ArrayNode) root;
                        else if (root.has("items") && root.get("items").isArray()) nodes = (ArrayNode) root.get("items");

                        if (nodes == null || nodes.size() == 0) return null;

                        List<InventoryItem> result = new ArrayList<>();
                        for (JsonNode n : nodes) {
                            try {
                                InventoryItem it = new InventoryItem();
                                if (n.has("sku")) it.setSku(n.get("sku").asText());
                                if (n.has("name")) it.setName(n.get("name").asText());
                                if (n.has("category")) it.setCategory(n.get("category").asText());
                                if (n.has("quantity") && n.get("quantity").isInt()) it.setQuantity(n.get("quantity").asInt());
                                if (n.has("unitPrice") && !n.get("unitPrice").isNull()) {
                                    try { it.setUnitPrice(new java.math.BigDecimal(n.get("unitPrice").asText())); } catch (Exception ex) { }
                                }
                                if (n.has("location")) it.setLocation(n.get("location").asText());
                                if (n.has("sourceId")) it.setSourceId(n.get("sourceId").asText());
                                if (n.has("lastUpdated")) {
                                    try { it.setLastUpdated(OffsetDateTimeParser.parse(n.get("lastUpdated").asText())); } catch (Exception ex) { }
                                }
                                // set status to PERSISTED when saving externally sourced items
                                it.setStatus("PERSISTED");
                                result.add(it);
                            } catch (Exception ex) {
                                logger.warn("Skipping invalid external item: {}", ex.getMessage());
                            }
                        }
                        return result;
                    } else {
                        logger.warn("External API returned status {}", response.statusCode());
                    }
                } catch (IOException | InterruptedException e) {
                    logger.warn("Attempt {} failed calling external API: {}", attempts, e.getMessage());
                    Thread.sleep(500L * attempts);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to call external API: {}", e.getMessage(), e);
        }
        return null;
    }

    private String urlEncode(String s) {
        try { return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8); } catch (Exception e) { return s; }
    }

    // Minimal parser to convert ISO datetimes to OffsetDateTime without adding new imports in signature
    private static class OffsetDateTimeParser {
        static java.time.OffsetDateTime parse(String s) {
            try { return java.time.OffsetDateTime.parse(s); } catch (Exception e) { return null; }
        }
    }
}
