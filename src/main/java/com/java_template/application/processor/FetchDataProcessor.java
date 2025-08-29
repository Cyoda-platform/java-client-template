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
import java.time.Instant;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;
import java.util.ArrayList;
import java.util.List;

@Component
public class FetchDataProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FetchDataProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Autowired
    public FetchDataProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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
        if (job == null) {
            return null;
        }

        // Update job status to RUNNING and set lastRunAt
        try {
            job.setStatus("RUNNING");
            job.setLastRunAt(Instant.now().toString());
        } catch (Exception ex) {
            logger.warn("Unable to update job running state: {}", ex.getMessage());
        }

        String baseUrl = job.getSourceUrl();
        if (baseUrl == null) {
            logger.error("Source URL is null for job {}", job.getJobId());
            job.setStatus("FAILED");
            return job;
        }

        boolean anyFailure = false;

        // Define endpoints to fetch - according to functional requirements we expect products, sales, inventory
        String[] endpoints = new String[]{"/products", "/sales", "/inventory"};

        // Prepare containers for parsed business entities
        List<Product> productsToPersist = new ArrayList<>();
        List<SalesRecord> salesToPersist = new ArrayList<>();
        List<InventorySnapshot> inventoryToPersist = new ArrayList<>();

        for (String endpoint : endpoints) {
            String url = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) + endpoint : baseUrl + endpoint;
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                String body = response.body();

                if (status >= 200 && status < 300 && body != null && !body.isBlank()) {
                    JsonNode root = objectMapper.readTree(body);

                    if (endpoint.endsWith("/products")) {
                        if (root.isArray()) {
                            for (JsonNode node : root) {
                                try {
                                    Product p = new Product();
                                    // Map common fields if present. Use safe lookups.
                                    if (node.hasNonNull("productId")) p.setProductId(node.get("productId").asText());
                                    else if (node.hasNonNull("id")) p.setProductId(node.get("id").asText());
                                    if (node.hasNonNull("name")) p.setName(node.get("name").asText());
                                    if (node.hasNonNull("category")) p.setCategory(node.get("category").asText());
                                    if (node.hasNonNull("price")) p.setPrice(node.get("price").isNumber() ? node.get("price").asDouble() : null);
                                    // store raw node as metadata string
                                    p.setMetadata(objectMapper.writeValueAsString(node));
                                    // Only add valid products (use entity validation)
                                    if (p.isValid()) productsToPersist.add(p);
                                    else {
                                        logger.debug("Skipping invalid product parsed from payload: {}", node.toString());
                                    }
                                } catch (Exception ex) {
                                    logger.warn("Failed to parse product node: {}", ex.getMessage());
                                }
                            }
                        } else if (root.isObject()) {
                            JsonNode node = root;
                            try {
                                Product p = new Product();
                                if (node.hasNonNull("productId")) p.setProductId(node.get("productId").asText());
                                else if (node.hasNonNull("id")) p.setProductId(node.get("id").asText());
                                if (node.hasNonNull("name")) p.setName(node.get("name").asText());
                                if (node.hasNonNull("category")) p.setCategory(node.get("category").asText());
                                if (node.hasNonNull("price")) p.setPrice(node.get("price").isNumber() ? node.get("price").asDouble() : null);
                                p.setMetadata(objectMapper.writeValueAsString(node));
                                if (p.isValid()) productsToPersist.add(p);
                                else logger.debug("Skipping invalid product parsed from payload: {}", node.toString());
                            } catch (Exception ex) {
                                logger.warn("Failed to parse product object: {}", ex.getMessage());
                            }
                        }
                    } else if (endpoint.endsWith("/sales")) {
                        if (root.isArray()) {
                            for (JsonNode node : root) {
                                try {
                                    SalesRecord s = new SalesRecord();
                                    if (node.hasNonNull("recordId")) s.setRecordId(node.get("recordId").asText());
                                    else if (node.hasNonNull("id")) s.setRecordId(node.get("id").asText());
                                    if (node.hasNonNull("dateSold")) s.setDateSold(node.get("dateSold").asText());
                                    if (node.hasNonNull("productId")) s.setProductId(node.get("productId").asText());
                                    if (node.hasNonNull("quantity")) s.setQuantity(node.get("quantity").isInt() ? node.get("quantity").asInt() : (node.get("quantity").isNumber() ? node.get("quantity").asInt() : null));
                                    if (node.hasNonNull("revenue")) s.setRevenue(node.get("revenue").isNumber() ? node.get("revenue").asDouble() : null);
                                    s.setRawSource(node.toString());
                                    if (s.isValid()) salesToPersist.add(s);
                                    else logger.debug("Skipping invalid sales record parsed from payload: {}", node.toString());
                                } catch (Exception ex) {
                                    logger.warn("Failed to parse sales node: {}", ex.getMessage());
                                }
                            }
                        } else if (root.isObject()) {
                            JsonNode node = root;
                            try {
                                SalesRecord s = new SalesRecord();
                                if (node.hasNonNull("recordId")) s.setRecordId(node.get("recordId").asText());
                                else if (node.hasNonNull("id")) s.setRecordId(node.get("id").asText());
                                if (node.hasNonNull("dateSold")) s.setDateSold(node.get("dateSold").asText());
                                if (node.hasNonNull("productId")) s.setProductId(node.get("productId").asText());
                                if (node.hasNonNull("quantity")) s.setQuantity(node.get("quantity").isInt() ? node.get("quantity").asInt() : (node.get("quantity").isNumber() ? node.get("quantity").asInt() : null));
                                if (node.hasNonNull("revenue")) s.setRevenue(node.get("revenue").isNumber() ? node.get("revenue").asDouble() : null);
                                s.setRawSource(node.toString());
                                if (s.isValid()) salesToPersist.add(s);
                                else logger.debug("Skipping invalid sales record parsed from payload: {}", node.toString());
                            } catch (Exception ex) {
                                logger.warn("Failed to parse sales object: {}", ex.getMessage());
                            }
                        }
                    } else if (endpoint.endsWith("/inventory")) {
                        if (root.isArray()) {
                            for (JsonNode node : root) {
                                try {
                                    InventorySnapshot inv = new InventorySnapshot();
                                    if (node.hasNonNull("snapshotId")) inv.setSnapshotId(node.get("snapshotId").asText());
                                    else if (node.hasNonNull("id")) inv.setSnapshotId(node.get("id").asText());
                                    if (node.hasNonNull("productId")) inv.setProductId(node.get("productId").asText());
                                    if (node.hasNonNull("snapshotAt")) inv.setSnapshotAt(node.get("snapshotAt").asText());
                                    if (node.hasNonNull("stockLevel")) inv.setStockLevel(node.get("stockLevel").isInt() ? node.get("stockLevel").asInt() : null);
                                    if (node.hasNonNull("restockThreshold")) inv.setRestockThreshold(node.get("restockThreshold").isInt() ? node.get("restockThreshold").asInt() : null);
                                    if (inv.isValid()) inventoryToPersist.add(inv);
                                    else logger.debug("Skipping invalid inventory snapshot parsed from payload: {}", node.toString());
                                } catch (Exception ex) {
                                    logger.warn("Failed to parse inventory node: {}", ex.getMessage());
                                }
                            }
                        } else if (root.isObject()) {
                            JsonNode node = root;
                            try {
                                InventorySnapshot inv = new InventorySnapshot();
                                if (node.hasNonNull("snapshotId")) inv.setSnapshotId(node.get("snapshotId").asText());
                                else if (node.hasNonNull("id")) inv.setSnapshotId(node.get("id").asText());
                                if (node.hasNonNull("productId")) inv.setProductId(node.get("productId").asText());
                                if (node.hasNonNull("snapshotAt")) inv.setSnapshotAt(node.get("snapshotAt").asText());
                                if (node.hasNonNull("stockLevel")) inv.setStockLevel(node.get("stockLevel").isInt() ? node.get("stockLevel").asInt() : null);
                                if (node.hasNonNull("restockThreshold")) inv.setRestockThreshold(node.get("restockThreshold").isInt() ? node.get("restockThreshold").asInt() : null);
                                if (inv.isValid()) inventoryToPersist.add(inv);
                                else logger.debug("Skipping invalid inventory snapshot parsed from payload: {}", node.toString());
                            } catch (Exception ex) {
                                logger.warn("Failed to parse inventory object: {}", ex.getMessage());
                            }
                        }
                    }
                } else {
                    anyFailure = true;
                    logger.warn("Failed fetch from {}: status={}, body={}", url, status, body);
                }
            } catch (Exception ex) {
                anyFailure = true;
                logger.error("Error fetching data from {}: {}", url, ex.getMessage(), ex);
            }
        }

        // Persist collected entities using EntityService - only other entities may be added/updated
        try {
            if (!productsToPersist.isEmpty()) {
                // addItems returns CompletableFuture<List<UUID>>
                CompletableFuture<List<java.util.UUID>> idsFuture = entityService.addItems(
                    Product.ENTITY_NAME,
                    Product.ENTITY_VERSION,
                    productsToPersist
                );
                idsFuture.get();
                logger.info("Persisted {} products", productsToPersist.size());
            }
            if (!salesToPersist.isEmpty()) {
                CompletableFuture<List<java.util.UUID>> idsFuture = entityService.addItems(
                    SalesRecord.ENTITY_NAME,
                    SalesRecord.ENTITY_VERSION,
                    salesToPersist
                );
                idsFuture.get();
                logger.info("Persisted {} sales records", salesToPersist.size());
            }
            if (!inventoryToPersist.isEmpty()) {
                CompletableFuture<List<java.util.UUID>> idsFuture = entityService.addItems(
                    InventorySnapshot.ENTITY_NAME,
                    InventorySnapshot.ENTITY_VERSION,
                    inventoryToPersist
                );
                idsFuture.get();
                logger.info("Persisted {} inventory snapshots", inventoryToPersist.size());
            }
        } catch (Exception ex) {
            anyFailure = true;
            logger.error("Failed to persist derived entities: {}", ex.getMessage(), ex);
        }

        // If this run is scheduled for Monday or today is Monday, enqueue WeeklyReport generation
        try {
            LocalDate today = LocalDate.now();
            boolean isMonday = today.getDayOfWeek() == DayOfWeek.MONDAY;
            // Also allow manual scheduleCron that contains MON as a hint
            boolean cronHasMonday = job.getScheduleCron() != null && job.getScheduleCron().toUpperCase().contains("MON");
            if (isMonday || cronHasMonday) {
                WeeklyReport report = new WeeklyReport();
                LocalDate weekStart = today.with(DayOfWeek.MONDAY);
                report.setWeekStart(weekStart.toString());
                report.setGeneratedAt(Instant.now().toString());
                report.setReportId("weekly-summary-" + weekStart.toString());
                report.setStatus("GENERATING");
                // summary and attachmentUrl left null for downstream processors
                try {
                    CompletableFuture<java.util.UUID> reportFuture = entityService.addItem(
                        WeeklyReport.ENTITY_NAME,
                        WeeklyReport.ENTITY_VERSION,
                        report
                    );
                    reportFuture.get();
                    logger.info("Enqueued WeeklyReport generation for weekStart={}", report.getWeekStart());
                } catch (Exception ex) {
                    logger.error("Failed to enqueue WeeklyReport: {}", ex.getMessage(), ex);
                }
            }
        } catch (Exception ex) {
            logger.warn("Error while deciding about WeeklyReport generation: {}", ex.getMessage(), ex);
        }

        // Finalize job status
        if (anyFailure) {
            job.setStatus("FAILED");
        } else {
            job.setStatus("COMPLETED");
        }
        job.setLastRunAt(Instant.now().toString());

        return job;
    }
}