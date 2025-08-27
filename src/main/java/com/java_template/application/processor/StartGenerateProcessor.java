package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.product.version_1.Product;
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

import java.util.HashMap;
import java.util.Map;

@Component
public class StartGenerateProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(StartGenerateProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public StartGenerateProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
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
        ReportJob entity = context.entity();

        try {
            logger.info("StartGenerateProcessor: computing KPIs for ReportJob id={}, period {} -> {}",
                    entity.getId(), entity.getPeriodStart(), entity.getPeriodEnd());

            // Build time range for lastUpdated filtering (assume ISO dates)
            String fromTimestamp = entity.getPeriodStart() + "T00:00:00Z";
            String toTimestamp = entity.getPeriodEnd() + "T23:59:59Z";

            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.lastUpdated", "GREATER_THAN_OR_EQUAL", fromTimestamp),
                    Condition.of("$.lastUpdated", "LESS_THAN_OR_EQUAL", toTimestamp)
            );

            // Fetch Product items in the given period (inMemory=true)
            ArrayNode items = entityService.getItemsByCondition(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION),
                    condition,
                    true
            ).join();

            // Aggregate KPIs by category and overall
            Map<String, CategoryKpi> categoryMap = new HashMap<>();
            long totalSalesVolume = 0L;
            double totalRevenue = 0.0;

            if (items != null) {
                for (int i = 0; i < items.size(); i++) {
                    ObjectNode node = (ObjectNode) items.get(i);
                    try {
                        Product p = objectMapper.convertValue(node, Product.class);

                        int salesVol = p.getTotalSalesVolume() != null ? p.getTotalSalesVolume() : 0;
                        double revenue = p.getTotalRevenue() != null ? p.getTotalRevenue() : 0.0;
                        int inventory = p.getInventoryOnHand() != null ? p.getInventoryOnHand() : 0;
                        String category = p.getCategory() != null ? p.getCategory() : "UNSPECIFIED";

                        CategoryKpi kpi = categoryMap.computeIfAbsent(category, k -> new CategoryKpi());
                        kpi.salesVolume += salesVol;
                        kpi.revenue += revenue;
                        kpi.inventorySum += inventory;
                        kpi.productCount += 1;

                        totalSalesVolume += salesVol;
                        totalRevenue += revenue;
                    } catch (IllegalArgumentException ex) {
                        logger.warn("Skipping product record due to conversion error: {}", ex.getMessage());
                    }
                }
            }

            // Compute derived KPIs (e.g., turnover) and log results
            StringBuilder kpiSummary = new StringBuilder();
            kpiSummary.append("Report KPIs Overview: totalProducts=").append(items == null ? 0 : items.size())
                    .append(", totalSalesVolume=").append(totalSalesVolume)
                    .append(", totalRevenue=").append(totalRevenue).append("\n");

            for (Map.Entry<String, CategoryKpi> e : categoryMap.entrySet()) {
                CategoryKpi c = e.getValue();
                double avgInventory = c.productCount > 0 ? ((double) c.inventorySum / c.productCount) : 0.0;
                double turnover = avgInventory > 0 ? (c.revenue / avgInventory) : 0.0;
                kpiSummary.append("Category=").append(e.getKey())
                        .append(", salesVolume=").append(c.salesVolume)
                        .append(", revenue=").append(c.revenue)
                        .append(", avgInventory=").append(String.format("%.2f", avgInventory))
                        .append(", turnover=").append(String.format("%.2f", turnover))
                        .append("\n");
            }

            logger.info(kpiSummary.toString());

            // Store computed state in the ReportJob context: update status to GENERATING
            entity.setStatus("GENERATING");

            // Note: There is no dedicated field on ReportJob to persist KPI table; we log results.
            // Subsequent processors (AttachFilesProcessor) will pick up the status and expected context.

        } catch (Exception ex) {
            logger.error("Error while generating KPIs for ReportJob id={}: {}", entity.getId(), ex.getMessage(), ex);
            // set status to FAILED on unexpected errors
            entity.setStatus("FAILED");
        }

        return entity;
    }

    private static class CategoryKpi {
        long salesVolume = 0L;
        double revenue = 0.0;
        long inventorySum = 0L;
        int productCount = 0;
    }
}