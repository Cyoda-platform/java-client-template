package com.java_template.application.processor;
import com.java_template.application.entity.ingestionjob.version_1.IngestionJob;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.application.entity.salesrecord.version_1.SalesRecord;
import com.java_template.application.entity.inventorysnapshot.version_1.InventorySnapshot;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Component
public class TransformProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(TransformProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Autowired
    public TransformProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing IngestionJob for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(IngestionJob.class)
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

    private boolean isValidEntity(IngestionJob entity) {
        return entity != null && entity.isValid();
    }

    private IngestionJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<IngestionJob> context) {
        IngestionJob job = context.entity();

        // Business logic:
        // - Use the job.sourceUrl to retrieve data (products, sales, inventory)
        // - Transform retrieved JSON payloads into Product, SalesRecord, InventorySnapshot entities
        // - Persist derived entities using entityService.addItems(...)
        // - Update job metadata (lastRunAt and status) on the job entity (do not call entityService.updateItem for the triggering entity)

        List<Product> products = new ArrayList<>();
        List<SalesRecord> sales = new ArrayList<>();
        List<InventorySnapshot> inventories = new ArrayList<>();

        String baseUrl = job.getSourceUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            logger.warn("IngestionJob has empty sourceUrl, marking as FAILED");
            job.setStatus("FAILED");
            job.setLastRunAt(Instant.now().toString());
            return job;
        }

        // Define endpoints expected by the ingestion logic.
        String[] endpoints = new String[]{"/products", "/sales", "/inventory"};

        boolean anySuccess = false;

        for (String endpoint : endpoints) {
            try {
                String target = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) + endpoint
                        : baseUrl + endpoint;
                HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(URI.create(target))
                        .GET()
                        .header("Accept", "application/json")
                        .build();

                logger.info("Fetching data from {}", target);
                HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                int statusCode = response.statusCode();
                if (statusCode >= 200 && statusCode < 300) {
                    String body = response.body();
                    if (body == null || body.isBlank()) {
                        logger.info("Empty response from {}", target);
                        continue;
                    }
                    JsonNode root = objectMapper.readTree(body);

                    if (endpoint.equals("/products")) {
                        if (root.isArray()) {
                            for (JsonNode node : root) {
                                try {
                                    Product p = objectMapper.treeToValue(node, Product.class);
                                    // If mapping didn't populate required fields, attempt to fill minimal ones
                                    if (p.getProductId() == null || p.getProductId().isBlank()) {
                                        // try name or generate from node
                                        String genId = node.has("id") ? node.get("id").asText() : "prod-" + Instant.now().toEpochMilli();
                                        p.setProductId(genId);
                                    }
                                    if (p.getName() == null && node.has("name")) {
                                        p.setName(node.get("name").asText());
                                    }
                                    // Ensure price is not null
                                    if (p.getPrice() == null) {
                                        if (node.has("price") && node.get("price").isNumber()) {
                                            p.setPrice(node.get("price").asDouble());
                                        } else {
                                            p.setPrice(0.0);
                                        }
                                    }
                                    // metadata: serialize remaining node if metadata not present
                                    if (p.getMetadata() == null && node.isObject()) {
                                        p.setMetadata(node.toString());
                                    }
                                    // Only add valid products
                                    if (p.isValid()) {
                                        products.add(p);
                                    } else {
                                        logger.debug("Skipping invalid product mapped from node: {}", node.toString());
                                    }
                                } catch (Exception ex) {
                                    logger.warn("Failed to map product node, storing raw metadata. Error: {}", ex.getMessage());
                                    try {
                                        Product p = new Product();
                                        p.setProductId("prod-" + Instant.now().toEpochMilli());
                                        p.setName(node.has("name") ? node.get("name").asText() : "unknown");
                                        p.setPrice(0.0);
                                        p.setMetadata(node.toString());
                                        if (p.isValid()) {
                                            products.add(p);
                                        }
                                    } catch (Exception ignore) {}
                                }
                            }
                        } else if (root.isObject()) {
                            try {
                                Product p = objectMapper.treeToValue(root, Product.class);
                                if (p.getProductId() == null || p.getProductId().isBlank()) {
                                    p.setProductId("prod-" + Instant.now().toEpochMilli());
                                }
                                if (p.getPrice() == null) p.setPrice(0.0);
                                if (p.getMetadata() == null) p.setMetadata(root.toString());
                                if (p.isValid()) products.add(p);
                            } catch (Exception ex) {
                                logger.warn("Failed to map single product object: {}", ex.getMessage());
                            }
                        }
                        // Persist products if any
                        if (!products.isEmpty()) {
                            try {
                                entityService.addItems(Product.ENTITY_NAME, Product.ENTITY_VERSION, products).get();
                                anySuccess = true;
                                logger.info("Persisted {} products", products.size());
                            } catch (InterruptedException | ExecutionException e) {
                                logger.error("Failed to persist products: {}", e.getMessage(), e);
                            }
                        }
                    } else if (endpoint.equals("/sales")) {
                        if (root.isArray()) {
                            for (JsonNode node : root) {
                                try {
                                    SalesRecord r = objectMapper.treeToValue(node, SalesRecord.class);
                                    // Ensure required fields are present, otherwise try to fill minimal values
                                    if (r.getRecordId() == null || r.getRecordId().isBlank()) {
                                        r.setRecordId("sales-" + Instant.now().toEpochMilli());
                                    }
                                    if (r.getDateSold() == null && node.has("dateSold")) {
                                        r.setDateSold(node.get("dateSold").asText());
                                    }
                                    if (r.getProductId() == null && node.has("productId")) {
                                        r.setProductId(node.get("productId").asText());
                                    }
                                    if (r.getQuantity() == null && node.has("quantity")) {
                                        r.setQuantity(node.get("quantity").asInt());
                                    }
                                    if (r.getRevenue() == null && node.has("revenue")) {
                                        r.setRevenue(node.get("revenue").asDouble());
                                    }
                                    if (r.getRawSource() == null) {
                                        r.setRawSource(node.toString());
                                    }
                                    if (r.isValid()) {
                                        sales.add(r);
                                    } else {
                                        logger.debug("Skipping invalid sales record mapped from node: {}", node.toString());
                                    }
                                } catch (Exception ex) {
                                    logger.warn("Failed to map sales node, creating minimal SalesRecord. Error: {}", ex.getMessage());
                                    try {
                                        SalesRecord r = new SalesRecord();
                                        r.setRecordId("sales-" + Instant.now().toEpochMilli());
                                        r.setDateSold(Instant.now().toString());
                                        r.setProductId(node.has("productId") ? node.get("productId").asText() : "unknown");
                                        r.setQuantity(node.has("quantity") ? node.get("quantity").asInt() : 1);
                                        r.setRevenue(node.has("revenue") ? node.get("revenue").asDouble() : 0.0);
                                        r.setRawSource(node.toString());
                                        if (r.isValid()) sales.add(r);
                                    } catch (Exception ignore) {}
                                }
                            }
                        } else if (root.isObject()) {
                            try {
                                SalesRecord r = objectMapper.treeToValue(root, SalesRecord.class);
                                if (r.getRecordId() == null || r.getRecordId().isBlank()) r.setRecordId("sales-" + Instant.now().toEpochMilli());
                                if (r.getRawSource() == null) r.setRawSource(root.toString());
                                if (r.isValid()) sales.add(r);
                            } catch (Exception ex) {
                                logger.warn("Failed to map single sales object: {}", ex.getMessage());
                            }
                        }
                        if (!sales.isEmpty()) {
                            try {
                                entityService.addItems(SalesRecord.ENTITY_NAME, SalesRecord.ENTITY_VERSION, sales).get();
                                anySuccess = true;
                                logger.info("Persisted {} sales records", sales.size());
                            } catch (InterruptedException | ExecutionException e) {
                                logger.error("Failed to persist sales records: {}", e.getMessage(), e);
                            }
                        }
                    } else if (endpoint.equals("/inventory")) {
                        if (root.isArray()) {
                            for (JsonNode node : root) {
                                try {
                                    InventorySnapshot s = objectMapper.treeToValue(node, InventorySnapshot.class);
                                    if (s.getSnapshotId() == null || s.getSnapshotId().isBlank()) {
                                        s.setSnapshotId("snap-" + Instant.now().toEpochMilli());
                                    }
                                    if (s.getSnapshotAt() == null) s.setSnapshotAt(Instant.now().toString());
                                    if (s.getStockLevel() == null && node.has("stockLevel")) s.setStockLevel(node.get("stockLevel").asInt());
                                    if (s.getRestockThreshold() == null && node.has("restockThreshold")) s.setRestockThreshold(node.get("restockThreshold").asInt());
                                    if (s.isValid()) {
                                        inventories.add(s);
                                    } else {
                                        logger.debug("Skipping invalid inventory mapped from node: {}", node.toString());
                                    }
                                } catch (Exception ex) {
                                    logger.warn("Failed to map inventory node, creating minimal snapshot. Error: {}", ex.getMessage());
                                    try {
                                        InventorySnapshot s = new InventorySnapshot();
                                        s.setSnapshotId("snap-" + Instant.now().toEpochMilli());
                                        s.setProductId(node.has("productId") ? node.get("productId").asText() : "unknown");
                                        s.setSnapshotAt(Instant.now().toString());
                                        s.setStockLevel(node.has("stockLevel") ? node.get("stockLevel").asInt() : 0);
                                        s.setRestockThreshold(node.has("restockThreshold") ? node.get("restockThreshold").asInt() : 0);
                                        if (s.isValid()) inventories.add(s);
                                    } catch (Exception ignore) {}
                                }
                            }
                        } else if (root.isObject()) {
                            try {
                                InventorySnapshot s = objectMapper.treeToValue(root, InventorySnapshot.class);
                                if (s.getSnapshotId() == null || s.getSnapshotId().isBlank()) s.setSnapshotId("snap-" + Instant.now().toEpochMilli());
                                if (s.getSnapshotAt() == null) s.setSnapshotAt(Instant.now().toString());
                                if (s.isValid()) inventories.add(s);
                            } catch (Exception ex) {
                                logger.warn("Failed to map single inventory object: {}", ex.getMessage());
                            }
                        }
                        if (!inventories.isEmpty()) {
                            try {
                                entityService.addItems(InventorySnapshot.ENTITY_NAME, InventorySnapshot.ENTITY_VERSION, inventories).get();
                                anySuccess = true;
                                logger.info("Persisted {} inventory snapshots", inventories.size());
                            } catch (InterruptedException | ExecutionException e) {
                                logger.error("Failed to persist inventory snapshots: {}", e.getMessage(), e);
                            }
                        }
                    }

                } else {
                    logger.warn("Non-success response {} from {}", statusCode, target);
                }
            } catch (Exception e) {
                logger.error("Error while fetching or processing endpoint {}: {}", endpoint, e.getMessage(), e);
            }
        }

        // Update job metadata (do not call entityService.updateItem for this entity)
        job.setLastRunAt(Instant.now().toString());
        job.setStatus(anySuccess ? "COMPLETED" : "FAILED");

        if (!anySuccess) {
            logger.warn("TransformProcessor completed with no persisted entities for job {}", job.getJobId());
        } else {
            logger.info("TransformProcessor successfully persisted derived entities for job {}", job.getJobId());
        }

        return job;
    }
}