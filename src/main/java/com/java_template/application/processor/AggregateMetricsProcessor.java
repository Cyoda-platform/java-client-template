package com.java_template.application.processor;

import com.java_template.application.entity.inventoryreportjob.version_1.InventoryReportJob;
import com.java_template.application.entity.inventoryitem.version_1.InventoryItem;
import com.java_template.application.entity.inventoryreport.version_1.InventoryReport;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
public class AggregateMetricsProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AggregateMetricsProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public AggregateMetricsProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing AggregateMetrics for request: {}", request.getId());

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
            // Fetch persisted InventoryItem records according to filters (reuse DataSufficientCriterion logic)
            List<InventoryItem> items = fetchItemsForJob(job);

            // If no items found, create an EMPTY InventoryReport and move to FORMATTING so FormatReportProcessor can finalize
            InventoryReport report = new InventoryReport();
            report.setJobRef(job.getTechnicalId());
            report.setReportName(job.getJobName());
            report.setGeneratedAt(OffsetDateTime.now());

            if (items == null || items.isEmpty()) {
                report.setStatus("AGGREGATED_EMPTY");
                report.setSuggestion("No data available for the requested filters/metrics. Consider broadening filters or enabling price enrichment.");
                persistReportTransient(report);
                job.setReportRef(report.getTechnicalId());
                job.setStatus("FORMATTING");
                return job;
            }

            Map<String, Object> metrics = new HashMap<>();
            int totalCount = items.size();
            metrics.put("totalCount", totalCount);

            // avgPrice and totalValue
            BigDecimal sumPrice = BigDecimal.ZERO;
            int priceCount = 0;
            BigDecimal totalValue = BigDecimal.ZERO;
            for (InventoryItem it : items) {
                if (it.getUnitPrice() != null) {
                    sumPrice = sumPrice.add(it.getUnitPrice());
                    priceCount++;
                    if (it.getQuantity() != null) {
                        totalValue = totalValue.add(it.getUnitPrice().multiply(new BigDecimal(it.getQuantity())));
                    }
                }
            }
            if (priceCount > 0) {
                metrics.put("avgPrice", sumPrice.divide(new BigDecimal(priceCount), 2, RoundingMode.HALF_UP));
            } else {
                metrics.put("avgPrice", null);
            }
            metrics.put("totalValue", totalValue);

            // group by (only first groupBy field supported in prototype)
            List<Map<String, Object>> grouped = new ArrayList<>();
            if (job.getGroupBy() != null && !job.getGroupBy().isEmpty()) {
                String groupField = job.getGroupBy().get(0);
                Map<String, List<InventoryItem>> groups = new HashMap<>();
                for (InventoryItem it : items) {
                    String key = "UNKNOWN";
                    if ("category".equals(groupField) && it.getCategory() != null) key = it.getCategory();
                    if ("location".equals(groupField) && it.getLocation() != null) key = it.getLocation();
                    groups.computeIfAbsent(key, k -> new ArrayList<>()).add(it);
                }
                for (Map.Entry<String, List<InventoryItem>> e : groups.entrySet()) {
                    Map<String, Object> g = new HashMap<>();
                    g.put("groupKey", e.getKey());
                    g.put("count", e.getValue().size());
                    grouped.add(g);
                }
            }

            // Persist an intermediate InventoryReport so downstream formatting can pick it up and finalize presentation
            report.setStatus("AGGREGATED");
            report.setMetricsSummary(metrics);
            report.setGroupedSummaries(grouped);
            report.setRetentionUntil(job.getRetentionUntil());

            persistReportTransient(report);

            job.setReportRef(report.getTechnicalId());
            job.setStatus("FORMATTING");
        } catch (Exception e) {
            logger.error("Error aggregating metrics for job {}: {}", job.getTechnicalId(), e.getMessage(), e);
            // Persist a failed report so user sees an error
            try {
                InventoryReport rep = new InventoryReport();
                rep.setJobRef(job.getTechnicalId());
                rep.setReportName(job.getJobName());
                rep.setGeneratedAt(OffsetDateTime.now());
                rep.setStatus("FAILED");
                rep.setErrorMessage(e.getMessage());
                persistReportTransient(rep);
                job.setReportRef(rep.getTechnicalId());
            } catch (Exception ex) {
                logger.warn("Failed to persist failed aggregated report for job {}: {}", job.getTechnicalId(), ex.getMessage());
            }
            job.setStatus("FAILED");
        }
        return job;
    }

    private List<InventoryItem> fetchItemsForJob(InventoryReportJob job) throws Exception {
        try {
            com.java_template.common.util.SearchConditionRequest condition = null;
            if (job.getFilters() != null && job.getFilters().get("category") != null) {
                condition = com.java_template.common.util.SearchConditionRequest.group("AND",
                    com.java_template.common.util.Condition.of("$.category", "EQUALS", job.getFilters().get("category").toString())
                );
            } else if (job.getFilters() != null && job.getFilters().get("location") != null) {
                condition = com.java_template.common.util.SearchConditionRequest.group("AND",
                    com.java_template.common.util.Condition.of("$.location", "EQUALS", job.getFilters().get("location").toString())
                );
            }

            CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> itemsFuture;
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

            com.fasterxml.jackson.databind.node.ArrayNode itemsNode = itemsFuture.get();
            if (itemsNode == null || itemsNode.size() == 0) return Collections.emptyList();

            List<InventoryItem> list = new ArrayList<>();
            for (int i = 0; i < itemsNode.size(); i++) {
                com.fasterxml.jackson.databind.JsonNode node = itemsNode.get(i);
                try {
                    InventoryItem item = new InventoryItem();
                    if (node.has("technicalId")) item.setTechnicalId(node.get("technicalId").asText());
                    if (node.has("sku")) item.setSku(node.get("sku").asText());
                    if (node.has("name")) item.setName(node.get("name").asText());
                    if (node.has("category")) item.setCategory(node.get("category").asText());
                    if (node.has("quantity")) item.setQuantity(node.get("quantity").asInt());
                    if (node.has("location")) item.setLocation(node.get("location").asText());
                    if (node.has("sourceId")) item.setSourceId(node.get("sourceId").asText());
                    if (node.has("unitPrice") && !node.get("unitPrice").isNull()) {
                        try {
                            item.setUnitPrice(new java.math.BigDecimal(node.get("unitPrice").asText()));
                        } catch (Exception ex) {
                            // ignore parse error
                        }
                    }
                    list.add(item);
                } catch (Exception e) {
                    logger.warn("Skipping invalid fetched item: {}", e.getMessage());
                }
            }
            return list;
        } catch (Exception e) {
            logger.error("Failed to fetch items for job {}: {}", job.getTechnicalId(), e.getMessage(), e);
            throw e;
        }
    }

    private void persistReportTransient(InventoryReport report) {
        try {
            CompletableFuture<java.util.UUID> fut = entityService.addItem(
                InventoryReport.ENTITY_NAME,
                String.valueOf(InventoryReport.ENTITY_VERSION),
                report
            );
            java.util.UUID id = fut.get();
            if (id != null) {
                report.setTechnicalId(id.toString());
            } else {
                report.setTechnicalId(UUID.randomUUID().toString());
            }
        } catch (Exception e) {
            logger.error("Failed to persist aggregated InventoryReport: {}", e.getMessage(), e);
            report.setTechnicalId(UUID.randomUUID().toString());
        }
    }
}
