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
                // persist updated metrics by updating product entity
                try {
                    CompletableFuture<UUID> updated = entityService.updateItem(Product.ENTITY_NAME, String.valueOf(Product.ENTITY_VERSION), UUID.fromString(p.getTechnicalId()), p);
                    updated.get();
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
        if (node.has("technicalId")) p.setTechnicalId(node.get("technicalId").asText());
        if (node.has("price")) p.setPrice(node.get("price").asDouble());
        if (node.has("stockLevel")) p.setStockLevel(node.get("stockLevel").asInt());
        if (node.has("salesHistory") && node.get("salesHistory").isArray()) {
            // Not implementing detailed parsing for prototype
        }
        return p;
    }

    private void computeMetrics(Product p) {
        // Simple KPIs: salesVolume and revenue are mocked for prototype
        double salesVolume = 0.0;
        double revenue = 0.0;
        if (p.getPrice() > 0 && p.getStockLevel() >= 0) {
            // naive estimation: salesVolume = stockLevel * 0.1
            salesVolume = p.getStockLevel() * 0.1;
            revenue = salesVolume * p.getPrice();
        }
        if (p.getMetrics() == null) p.setMetrics(new java.util.HashMap<>());
        p.getMetrics().put("salesVolume", salesVolume);
        p.getMetrics().put("revenue", revenue);

        // turnoverRate = unitsSold / averageStockLevel (if averageStockLevel == 0 -> null)
        Double avgStock = p.getMetrics().containsKey("averageStockLevel") ? ((Number) p.getMetrics().get("averageStockLevel")).doubleValue() : null;
        if (avgStock == null || avgStock == 0.0) {
            p.getMetrics().put("turnoverRate", null);
        } else {
            p.getMetrics().put("turnoverRate", salesVolume / avgStock);
        }

        p.setLastUpdated(OffsetDateTime.now().toString());
    }
}
