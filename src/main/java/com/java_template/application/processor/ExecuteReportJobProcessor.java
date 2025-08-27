package com.java_template.application.processor;

import com.java_template.application.entity.reportjob.version_1.ReportJob;
import com.java_template.application.entity.report.version_1.Report;
import com.java_template.application.entity.report.version_1.Report.Metrics;
import com.java_template.application.entity.report.version_1.Report.Row;
import com.java_template.application.entity.report.version_1.Report.Visuals;
import com.java_template.application.entity.inventoryitem.version_1.InventoryItem;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class ExecuteReportJobProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ExecuteReportJobProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public ExecuteReportJobProcessor(SerializerFactory serializerFactory,
                                     EntityService entityService,
                                     ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ReportJob for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(ReportJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
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
        String technicalJobId = null;
        try {
            // Try to obtain triggering technical id if available in request context
            if (context.request() != null && context.request().getEntityId() != null) {
                technicalJobId = context.request().getEntityId();
            }
        } catch (Exception e) {
            // ignore - technical id is optional for building report job reference
        }

        logger.info("Executing ReportJob '{}' requestedBy='{}'", job.getTitle(), job.getRequestedBy());

        try {
            // Fetch InventoryItem entities (all items). We respect filters if simple filter available, but default to all.
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                InventoryItem.ENTITY_NAME,
                String.valueOf(InventoryItem.ENTITY_VERSION)
            );

            ArrayNode itemsArray = itemsFuture.join();
            int totalItems = 0;
            int totalQuantity = 0;
            double sumPrice = 0.0;
            int priceCount = 0;
            double totalValue = 0.0;

            List<Row> rows = new ArrayList<>();

            if (itemsArray != null) {
                for (JsonNode node : itemsArray) {
                    // Each node is expected to be an InventoryItem representation
                    try {
                        // Safely read fields using JsonNode accessors
                        String id = node.hasNonNull("id") ? node.get("id").asText() : null;
                        String name = node.hasNonNull("name") ? node.get("name").asText() : null;
                        Double price = node.hasNonNull("price") && !node.get("price").isNull() ? node.get("price").asDouble() : null;
                        Integer quantity = node.hasNonNull("quantity") && !node.get("quantity").isNull() ? node.get("quantity").asInt() : null;

                        // Build row only if id and name present (Report requires these)
                        Row r = new Row();
                        r.setId(id == null ? UUID.randomUUID().toString() : id);
                        r.setName(name == null ? "UNKNOWN" : name);
                        r.setPrice(price == null ? 0.0 : price);
                        r.setQuantity(quantity == null ? 0 : quantity);
                        double value = (r.getPrice() != null ? r.getPrice() : 0.0) * (r.getQuantity() != null ? r.getQuantity() : 0);
                        r.setValue(value);

                        rows.add(r);

                        // Aggregate metrics
                        totalItems++;
                        int q = r.getQuantity() != null ? r.getQuantity() : 0;
                        totalQuantity += q;
                        if (r.getPrice() != null) {
                            sumPrice += r.getPrice();
                            priceCount++;
                        }
                        totalValue += value;
                    } catch (Exception ex) {
                        // Skip malformed item but continue processing others
                        logger.warn("Skipping malformed inventory item node while computing report: {}", ex.getMessage());
                    }
                }
            }

            Double averagePrice = priceCount > 0 ? (sumPrice / priceCount) : 0.0;

            Metrics metrics = new Metrics();
            metrics.setAveragePrice(averagePrice);
            metrics.setTotalItems(totalItems);
            metrics.setTotalQuantity(totalQuantity);
            metrics.setTotalValue(totalValue);

            // Build Report
            Report report = new Report();
            String reportId = UUID.randomUUID().toString();
            report.setId(reportId);
            // set jobReference to triggering technical id if available, otherwise empty string
            report.setJobReference(technicalJobId != null ? technicalJobId : "");
            report.setGeneratedAt(Instant.now().toString());
            report.setMetrics(metrics);
            report.setRows(rows);
            // storageLocation is required by Report.isValid(); use a generated placeholder (could be replaced by real exporter)
            report.setStorageLocation("s3://reports/" + reportId);
            // summary - brief textual highlight
            report.setSummary(String.format("Generated %d items; totalQuantity=%d; totalValue=%.2f", totalItems, totalQuantity, totalValue));
            Visuals visuals = new Visuals();
            visuals.setChartType(job.getVisualization() != null ? job.getVisualization() : "table");
            visuals.setReference(report.getStorageLocation());
            report.setVisuals(visuals);

            // Persist Report entity (create new entity). We MUST NOT update the ReportJob via entityService; the job object will be persisted automatically.
            CompletableFuture<UUID> addFuture = entityService.addItem(
                Report.ENTITY_NAME,
                String.valueOf(Report.ENTITY_VERSION),
                report
            );

            UUID createdReportUuid = addFuture.join();
            if (createdReportUuid != null) {
                // Ensure report.id reflects stored id (string)
                report.setId(createdReportUuid.toString());
                logger.info("Report created with id={}", createdReportUuid.toString());
            } else {
                logger.warn("Report creation returned null UUID for job '{}'", job.getTitle());
            }

            // Update job status to COMPLETED
            job.setStatus("COMPLETED");

            // Note: ReportJob does not define a reportReference field per entity definition, so we cannot set it here.
            // Optionally log notification intent
            if (job.getNotify() != null && !job.getNotify().isBlank()) {
                logger.info("Notify target present for job '{}': {}", job.getTitle(), job.getNotify());
                // Notification sending is outside of this processor's responsibilities.
            }

        } catch (Exception e) {
            logger.error("Failed to execute ReportJob '{}': {}", job.getTitle(), e.getMessage(), e);
            // On failure, set job status to FAILED
            try {
                job.setStatus("FAILED");
            } catch (Exception ex) {
                logger.warn("Unable to set job status to FAILED due to: {}", ex.getMessage());
            }
        }

        return job;
    }
}