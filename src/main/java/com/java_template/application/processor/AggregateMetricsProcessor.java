package com.java_template.application.processor;

import com.java_template.application.entity.inventoryreportjob.version_1.InventoryReportJob;
import com.java_template.application.entity.inventoryitem.version_1.InventoryItem;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

@Component
public class AggregateMetricsProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AggregateMetricsProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public AggregateMetricsProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
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
            // In this simplified implementation we assume FetchInventoryProcessor stored fetched items in job.metadata.items
            // Since we can't rely on that, we'll compute metrics generically if job contains a transient list in a custom field
            Object fetched = job.getMetadata();
            List<InventoryItem> items = null;
            if (fetched instanceof List) {
                //noinspection unchecked
                items = (List<InventoryItem>) fetched;
            }

            if (items == null || items.isEmpty()) {
                // no data
                job.setStatus("FORMATTING"); // next stage will create EMPTY report
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
                metrics.put("avgPrice", sumPrice.divide(new BigDecimal(priceCount), 2, BigDecimal.ROUND_HALF_UP));
            } else {
                metrics.put("avgPrice", null);
            }
            metrics.put("totalValue", totalValue);

            // group by
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

            // Attach computed metrics to job metadata for downstream formatting
            Map<String, Object> computed = new HashMap<>();
            computed.put("metrics", metrics);
            computed.put("grouped", grouped);
            job.setMetadata(computed);

            job.setStatus("FORMATTING");
        } catch (Exception e) {
            logger.error("Error aggregating metrics for job {}: {}", job.getTechnicalId(), e.getMessage(), e);
            job.setStatus("FAILED");
        }
        return job;
    }
}
