package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.java_template.application.entity.inventoryitem.version_1.InventoryItem;
import com.java_template.application.entity.report.version_1.Report;
import com.java_template.application.entity.report.version_1.Report.Row;
import com.java_template.application.entity.report.version_1.Report.Metrics;
import com.java_template.application.entity.reportjob.version_1.ReportJob;
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
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.UUID;

@Component
public class PersistReportProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PersistReportProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PersistReportProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ReportJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(ReportJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ReportJob entity) {
        return entity != null && entity.isValid();
    }

    private ReportJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ReportJob> context) {
        ReportJob job = context.entity();
        String jobTechnicalId = null;
        try {
            // try to retrieve technical id from the request wrapped in context
            if (context.request() != null) {
                try {
                    jobTechnicalId = context.request().getEntityId();
                } catch (Exception ex) {
                    // ignore, we'll still proceed without explicit job id if missing
                }
            }
        } catch (Exception e) {
            // ignore - defensive
        }

        try {
            // Fetch inventory items according to job.filters (if provided)
            ArrayNode itemsArray = null;
            if (job.getFilters() != null && !job.getFilters().isEmpty()) {
                List<Condition> conditions = job.getFilters().entrySet().stream()
                    .filter(e -> e.getKey() != null && e.getValue() != null)
                    .map(e -> Condition.of("$." + e.getKey(), "EQUALS", e.getValue()))
                    .collect(Collectors.toList());

                SearchConditionRequest conditionRequest = SearchConditionRequest.group("AND", conditions.toArray(new Condition[0]));
                CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                    InventoryItem.ENTITY_NAME,
                    String.valueOf(InventoryItem.ENTITY_VERSION),
                    conditionRequest,
                    true
                );
                itemsArray = itemsFuture.join();
            } else {
                CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    InventoryItem.ENTITY_NAME,
                    String.valueOf(InventoryItem.ENTITY_VERSION)
                );
                itemsArray = itemsFuture.join();
            }

            if (itemsArray == null) {
                itemsArray = objectMapper.createArrayNode();
            }

            // Compute metrics and rows
            Set<String> distinctIds = new HashSet<>();
            int totalQuantity = 0;
            double totalValue = 0.0;
            double priceSumForAverage = 0.0;
            int priceCountForAverage = 0;

            List<Row> rows = new ArrayList<>();

            for (JsonNode node : itemsArray) {
                try {
                    InventoryItem inv = objectMapper.treeToValue(node, InventoryItem.class);
                    if (inv == null) continue;

                    // Track distinct items
                    if (inv.getId() != null) distinctIds.add(inv.getId());

                    int qty = inv.getQuantity() == null ? 0 : inv.getQuantity();
                    double price = inv.getPrice() == null ? 0.0 : inv.getPrice();
                    double value = price * qty;

                    totalQuantity += qty;
                    totalValue += value;
                    if (inv.getPrice() != null) {
                        priceSumForAverage += inv.getPrice();
                        priceCountForAverage++;
                    }

                    Row r = new Row();
                    r.setId(inv.getId());
                    r.setName(inv.getName());
                    r.setPrice(inv.getPrice());
                    r.setQuantity(inv.getQuantity());
                    r.setValue(value);
                    rows.add(r);
                } catch (Exception ex) {
                    logger.warn("Failed to parse InventoryItem node into object: {}", ex.getMessage());
                }
            }

            double averagePrice = 0.0;
            if (priceCountForAverage > 0) {
                averagePrice = priceSumForAverage / priceCountForAverage;
            }

            Metrics metrics = new Metrics();
            metrics.setTotalItems(distinctIds.size());
            metrics.setTotalQuantity(totalQuantity);
            metrics.setTotalValue(totalValue);
            metrics.setAveragePrice(averagePrice);

            // Build Report
            Report report = new Report();
            String reportId = UUID.randomUUID().toString();
            report.setId(reportId);
            report.setJobReference(jobTechnicalId != null ? jobTechnicalId : "");
            report.setGeneratedAt(Instant.now().toString());
            report.setMetrics(metrics);
            report.setRows(rows);
            // storageLocation required by Report.isValid(); use a generated reference
            report.setStorageLocation("generated://" + reportId);
            // summary: basic textual highlight
            String summary = String.format("Total items: %d, Total quantity: %d, Total value: %.2f",
                metrics.getTotalItems(), metrics.getTotalQuantity(), metrics.getTotalValue());
            report.setSummary(summary);
            // Do not set visuals (optional). Keep null to avoid invalidation.

            // Persist Report (this is a different entity; allowed)
            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                Report.ENTITY_NAME,
                String.valueOf(Report.ENTITY_VERSION),
                report
            );
            UUID persistedId = idFuture.join();
            logger.info("Persisted Report with id (future): {}", persistedId);

            // Update job state (modify the entity — Cyoda will persist the triggering entity)
            job.setStatus("COMPLETED");
            // Note: ReportJob class does not contain a reportReference field in codebase,
            // so we cannot set it here (must not invent properties).

        } catch (Exception ex) {
            logger.error("Error while persisting report for job: {}", ex.getMessage(), ex);
            // mark job as failed so it will be persisted by Cyoda
            try {
                job.setStatus("FAILED");
            } catch (Exception ignore) { }
        }

        return job;
    }
}