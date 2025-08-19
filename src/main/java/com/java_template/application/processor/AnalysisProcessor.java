package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.extractionjob.version_1.ExtractionJob;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class AnalysisProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AnalysisProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper mapper = new ObjectMapper();

    public AnalysisProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Analysis for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(ExtractionJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ExtractionJob entity) {
        return entity != null;
    }

    private ExtractionJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ExtractionJob> context) {
        ExtractionJob job = context.entity();
        // Query products updated since last run - prototype will fetch all products
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(Product.ENTITY_NAME, String.valueOf(Product.ENTITY_VERSION));
            ArrayNode items = itemsFuture.get();
            if (items == null || items.size() == 0) {
                logger.warn("No products to analyze for jobId={}", job.getJobId());
                return job;
            }

            Iterator<com.fasterxml.jackson.databind.JsonNode> it = items.elements();
            while (it.hasNext()) {
                ObjectNode node = (ObjectNode) it.next();
                Product p = mapNodeToProduct(node);
                computeMetrics(p);
                // persist updated metrics by updating product entity via entityService using technicalId from stored node
                try {
                    if (node.has("technicalId")) {
                        String tech = node.get("technicalId").asText();
                        CompletableFuture<UUID> updated = entityService.updateItem(Product.ENTITY_NAME, String.valueOf(Product.ENTITY_VERSION), UUID.fromString(tech), p);
                        updated.get();
                    } else {
                        logger.warn("Stored product missing technicalId for productId={}", p.getProductId());
                    }
                } catch (Exception e) {
                    logger.warn("Failed to update product metrics productId={} : {}", p.getProductId(), e.getMessage());
                }
            }

            logger.info("AnalysisProcessor completed for jobId={}", job.getJobId());
        } catch (Exception e) {
            logger.error("Error during analysis for jobId={}", job.getJobId(), e);
        }
        return job;
    }

    private Product mapNodeToProduct(ObjectNode node) {
        Product p = new Product();
        if (node.has("productId")) p.setProductId(node.get("productId").asText());
        if (node.has("name")) p.setName(node.get("name").asText());
        if (node.has("category")) p.setCategory(node.get("category").asText());
        if (node.has("sku")) p.setSku(node.get("sku").asText());
        if (node.has("price")) p.setPrice(node.get("price").asDouble());
        if (node.has("stockLevel")) p.setStockLevel(node.get("stockLevel").asInt());
        // salesHistory and other complex fields left as-is
        return p;
    }

    private void computeMetrics(Product p) {
        // Compute KPIs based on available fields
        double salesVolume = 0.0;
        double revenue = 0.0;
        if (p.getSalesHistory() != null && !p.getSalesHistory().isEmpty()) {
            for (Object entryObj : p.getSalesHistory()) {
                if (entryObj instanceof java.util.Map) {
                    java.util.Map<?, ?> entry = (java.util.Map<?, ?>) entryObj;
                    Number units = entry.get("unitsSold") instanceof Number ? (Number) entry.get("unitsSold") : null;
                    Number rev = entry.get("revenue") instanceof Number ? (Number) entry.get("revenue") : null;
                    if (units != null) salesVolume += units.doubleValue();
                    if (rev != null) revenue += rev.doubleValue();
                }
            }
        } else {
            if (p.getStockLevel() != null) salesVolume = p.getStockLevel() * 0.1;
            if (p.getPrice() != null) revenue = salesVolume * p.getPrice();
        }

        if (p.getMetrics() == null) p.setMetrics(new java.util.HashMap<>());
        p.getMetrics().put("salesVolume", salesVolume);
        p.getMetrics().put("revenue", revenue);

        Double avgStock = null;
        if (p.getMetrics().containsKey("averageStockLevel")) {
            Object o = p.getMetrics().get("averageStockLevel");
            if (o instanceof Number) avgStock = ((Number) o).doubleValue();
        }
        if (avgStock == null || avgStock == 0.0) {
            p.getMetrics().put("turnoverRate", null);
        } else {
            p.getMetrics().put("turnoverRate", salesVolume / avgStock);
        }

        p.setLastUpdated(OffsetDateTime.now().toString());
    }
}
