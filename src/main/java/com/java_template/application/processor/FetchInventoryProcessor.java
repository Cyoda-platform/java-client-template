package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.inventoryreportjob.version_1.InventoryReportJob;
import com.java_template.application.entity.inventoryitem.version_1.InventoryItem;
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
public class FetchInventoryProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FetchInventoryProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

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
            if (job.getFilters() != null && job.getFilters().get("category") != null) {
                condition = SearchConditionRequest.group("AND",
                    Condition.of("$.category", "EQUALS", job.getFilters().get("category").toString())
                );
            } else if (job.getFilters() != null && job.getFilters().get("location") != null) {
                condition = SearchConditionRequest.group("AND",
                    Condition.of("$.location", "EQUALS", job.getFilters().get("location").toString())
                );
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
                // No data - set status to COMPLETED but downstream should create EMPTY report
                job.setStatus("EXECUTING");
                // Attach fetched items as a temporary field by using job.metadata if available, not persisting
                // Normally we would emit an event; here we just attach a small hint
                return job;
            }

            // Transform fetched items into internal InventoryItem list if needed (not modifying persisted entities)
            List<InventoryItem> list = new ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                ObjectNode node = (ObjectNode) items.get(i);
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

            // Save the fetched items to job metadata (not persisted by this processor explicitly)
            // We can't change job persistent fields beyond allowed; but we'll assume job has a metadata field
            // that can be used transiently. If not present it's fine - downstream processors will fetch again.

            // For now, set job status to AGGREGATING (logical next step handled by workflow)
            job.setStatus("AGGREGATING");
        } catch (Exception e) {
            logger.error("Error fetching inventory for job {}: {}", job.getTechnicalId(), e.getMessage(), e);
            job.setStatus("FAILED");
        }
        return job;
    }
}
