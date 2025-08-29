package com.java_template.application.processor;

import com.java_template.application.entity.ingestionjob.version_1.IngestionJob;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.application.entity.salesrecord.version_1.SalesRecord;
import com.java_template.application.entity.inventorysnapshot.version_1.InventorySnapshot;
import com.java_template.application.entity.weeklyreport.version_1.WeeklyReport;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.*;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.time.format.DateTimeFormatter;

@Component
public class PersistProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PersistProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public PersistProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
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
        // Mark job as RUNNING (state will be persisted by Cyoda workflow)
        try {
            job.setStatus("RUNNING");
            String baseUrl = job.getSourceUrl();
            if (baseUrl == null) baseUrl = "";

            List<Product> products = new ArrayList<>();
            List<SalesRecord> salesRecords = new ArrayList<>();
            List<InventorySnapshot> inventorySnapshots = new ArrayList<>();

            // Determine whether to request JSON (prefer JSON if listed)
            String acceptHeader = "application/json";
            String dataFormats = job.getDataFormats();
            if (dataFormats != null && dataFormats.toUpperCase().contains("XML") && !dataFormats.toUpperCase().contains("JSON")) {
                acceptHeader = "application/xml";
            }

            // Helper to fetch an endpoint
            HttpClient client = this.httpClient;

            // Define endpoints expected by ingestion job (based on functional requirements)
            Map<String, String> endpoints = new LinkedHashMap<>();
            endpoints.put("products", "/products");
            endpoints.put("sales", "/sales");
            endpoints.put("inventory", "/inventory");

            for (Map.Entry<String, String> e : endpoints.entrySet()) {
                String kind = e.getKey();
                String endpoint = e.getValue();
                String url = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) + endpoint : baseUrl + endpoint;
                try {
                    HttpRequest httpRequest = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .GET()
                            .header("Accept", acceptHeader)
                            .build();

                    HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                    int status = response.statusCode();
                    if (status >= 200 && status < 300 && response.body() != null && !response.body().isBlank()) {
                        String body = response.body();
                        // Try to parse JSON; if XML was returned, skip parsing (XML handling not implemented)
                        try {
                            JsonNode root = objectMapper.readTree(body);
                            if ("products".equals(kind)) {
                                // Products may be an array or wrapped in an object
                                Iterable<JsonNode> nodes = root.isArray()
                                        ? root
                                        : root.has("products") && root.get("products").isArray()
                                            ? root.get("products")
                                            : Collections.singletonList(root);
                                for (JsonNode n : nodes) {
                                    try {
                                        String pid = n.hasNonNull("productId") ? n.get("productId").asText() :
                                                n.hasNonNull("id") ? n.get("id").asText() : null;
                                        String name = n.hasNonNull("name") ? n.get("name").asText() : null;
                                        String category = n.hasNonNull("category") ? n.get("category").asText() : null;
                                        Double price = n.hasNonNull("price") ? n.get("price").asDouble() : 0.0;
                                        String metadata = objectMapper.writeValueAsString(n);

                                        if (pid == null || pid.isBlank() || name == null || name.isBlank() || price == null || price < 0.0) {
                                            // skip invalid product entry
                                            logger.warn("Skipping invalid product entry from {}: {}", url, n.toString());
                                            continue;
                                        }

                                        Product p = new Product();
                                        p.setProductId(pid);
                                        p.setName(name);
                                        p.setCategory(category);
                                        p.setPrice(price);
                                        p.setMetadata(metadata);
                                        products.add(p);
                                    } catch (Exception ex) {
                                        logger.warn("Failed to map product node: {}", ex.getMessage());
                                    }
                                }
                            } else if ("sales".equals(kind)) {
                                Iterable<JsonNode> nodes = root.isArray()
                                        ? root
                                        : root.has("sales") && root.get("sales").isArray()
                                            ? root.get("sales")
                                            : Collections.singletonList(root);
                                for (JsonNode n : nodes) {
                                    try {
                                        String recordId = n.hasNonNull("recordId") ? n.get("recordId").asText() : UUID.randomUUID().toString();
                                        String dateSold = n.hasNonNull("dateSold") ? n.get("dateSold").asText() :
                                                n.hasNonNull("date") ? n.get("date").asText() : Instant.now().toString();
                                        String productId = n.hasNonNull("productId") ? n.get("productId").asText() : null;
                                        Integer quantity = n.hasNonNull("quantity") ? n.get("quantity").asInt() : 1;
                                        Double revenue = n.hasNonNull("revenue") ? n.get("revenue").asDouble() : 0.0;
                                        String raw = objectMapper.writeValueAsString(n);

                                        if (recordId == null || recordId.isBlank() || dateSold == null || dateSold.isBlank()
                                                || productId == null || productId.isBlank() || quantity == null || quantity <= 0
                                                || revenue == null || revenue < 0.0) {
                                            logger.warn("Skipping invalid sales entry from {}: {}", url, n.toString());
                                            continue;
                                        }

                                        SalesRecord sr = new SalesRecord();
                                        sr.setRecordId(recordId);
                                        sr.setDateSold(dateSold);
                                        sr.setProductId(productId);
                                        sr.setQuantity(quantity);
                                        sr.setRevenue(revenue);
                                        sr.setRawSource(raw);
                                        salesRecords.add(sr);
                                    } catch (Exception ex) {
                                        logger.warn("Failed to map sales node: {}", ex.getMessage());
                                    }
                                }
                            } else if ("inventory".equals(kind)) {
                                Iterable<JsonNode> nodes = root.isArray()
                                        ? root
                                        : root.has("inventory") && root.get("inventory").isArray()
                                            ? root.get("inventory")
                                            : Collections.singletonList(root);
                                for (JsonNode n : nodes) {
                                    try {
                                        String snapshotId = n.hasNonNull("snapshotId") ? n.get("snapshotId").asText() : UUID.randomUUID().toString();
                                        String productId = n.hasNonNull("productId") ? n.get("productId").asText() : null;
                                        String snapshotAt = n.hasNonNull("snapshotAt") ? n.get("snapshotAt").asText() : Instant.now().toString();
                                        Integer stockLevel = n.hasNonNull("stockLevel") ? n.get("stockLevel").asInt() : 0;
                                        Integer restockThreshold = n.hasNonNull("restockThreshold") ? n.get("restockThreshold").asInt() : 0;

                                        if (snapshotId == null || snapshotId.isBlank() || productId == null || productId.isBlank()
                                                || snapshotAt == null || snapshotAt.isBlank()
                                                || stockLevel == null || stockLevel < 0
                                                || restockThreshold == null || restockThreshold < 0) {
                                            logger.warn("Skipping invalid inventory entry from {}: {}", url, n.toString());
                                            continue;
                                        }

                                        InventorySnapshot inv = new InventorySnapshot();
                                        inv.setSnapshotId(snapshotId);
                                        inv.setProductId(productId);
                                        inv.setSnapshotAt(snapshotAt);
                                        inv.setStockLevel(stockLevel);
                                        inv.setRestockThreshold(restockThreshold);
                                        inventorySnapshots.add(inv);
                                    } catch (Exception ex) {
                                        logger.warn("Failed to map inventory node: {}", ex.getMessage());
                                    }
                                }
                            }
                        } catch (Exception parseEx) {
                            logger.warn("Failed to parse response from {}: {}", url, parseEx.getMessage());
                        }
                    } else {
                        logger.warn("Non-success response fetching {}: status={}", url, status);
                    }
                } catch (Exception httpEx) {
                    logger.warn("Failed to call {}: {}", url, httpEx.getMessage());
                }
            }

            // Persist derived entities (only other entities, do not update the triggering IngestionJob via entityService)
            try {
                if (!products.isEmpty()) {
                    CompletableFuture<List<java.util.UUID>> added = entityService.addItems(Product.ENTITY_NAME, Product.ENTITY_VERSION, products);
                    List<java.util.UUID> ids = added.get();
                    logger.info("Persisted {} products for job {}: ids={}", ids != null ? ids.size() : 0, job.getJobId(), ids);
                }
                if (!salesRecords.isEmpty()) {
                    CompletableFuture<List<java.util.UUID>> added = entityService.addItems(SalesRecord.ENTITY_NAME, SalesRecord.ENTITY_VERSION, salesRecords);
                    List<java.util.UUID> ids = added.get();
                    logger.info("Persisted {} sales records for job {}: ids={}", ids != null ? ids.size() : 0, job.getJobId(), ids);
                }
                if (!inventorySnapshots.isEmpty()) {
                    CompletableFuture<List<java.util.UUID>> added = entityService.addItems(InventorySnapshot.ENTITY_NAME, InventorySnapshot.ENTITY_VERSION, inventorySnapshots);
                    List<java.util.UUID> ids = added.get();
                    logger.info("Persisted {} inventory snapshots for job {}: ids={}", ids != null ? ids.size() : 0, job.getJobId(), ids);
                }
            } catch (InterruptedException | ExecutionException ex) {
                logger.error("Failed to persist derived entities for job {}: {}", job.getJobId(), ex.getMessage(), ex);
                job.setStatus("FAILED");
                job.setLastRunAt(Instant.now().toString());
                return job;
            }

            // Update job final status and lastRunAt
            job.setLastRunAt(Instant.now().toString());
            job.setStatus("COMPLETED");

            // If run on Monday (UTC) or configured Monday cron, create a WeeklyReport orchestration entity to start report workflow
            try {
                LocalDate nowUtc = LocalDate.now(ZoneOffset.UTC);
                DayOfWeek today = nowUtc.getDayOfWeek();
                boolean isMondayRun = today == DayOfWeek.MONDAY;
                // Also check cron expression for "MON" presence as heuristic
                if (!isMondayRun && job.getScheduleCron() != null && job.getScheduleCron().toUpperCase().contains("MON")) {
                    isMondayRun = true;
                }

                if (isMondayRun) {
                    LocalDate weekStart = nowUtc.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                    String weekStartStr = weekStart.toString();
                    WeeklyReport wr = new WeeklyReport();
                    String reportId = (job.getJobId() != null ? job.getJobId() + "-" : "") + weekStartStr;
                    wr.setReportId(reportId);
                    wr.setWeekStart(weekStartStr);
                    wr.setGeneratedAt(Instant.now().toString());
                    wr.setStatus("GENERATING");
                    // summary and attachmentUrl left null for report generator processors to fill

                    try {
                        CompletableFuture<java.util.UUID> addedWr = entityService.addItem(WeeklyReport.ENTITY_NAME, WeeklyReport.ENTITY_VERSION, wr);
                        java.util.UUID wrId = addedWr.get();
                        logger.info("Enqueued WeeklyReport {} (technicalId={}) for job {}", reportId, wrId, job.getJobId());
                    } catch (InterruptedException | ExecutionException ex) {
                        logger.warn("Failed to enqueue WeeklyReport for job {}: {}", job.getJobId(), ex.getMessage());
                    }
                }
            } catch (Exception e) {
                logger.warn("Failed to evaluate or enqueue WeeklyReport: {}", e.getMessage());
            }

        } catch (Exception ex) {
            logger.error("Unhandled error during persist processing for job {}: {}", job.getJobId(), ex.getMessage(), ex);
            job.setStatus("FAILED");
            job.setLastRunAt(Instant.now().toString());
        }

        return job;
    }
}