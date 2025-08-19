package com.java_template.application.processor;

import com.java_template.application.entity.extractionjob.version_1.ExtractionJob;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class TransformProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(TransformProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    @Autowired
    public TransformProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Transform for request: {}", request.getId());

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
        return entity != null && entity.getParameters() != null;
    }

    private ExtractionJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ExtractionJob> context) {
        ExtractionJob job = context.entity();
        // In real implementation we would read RawData events. For prototype, assume data available in job.parameters.rawData
        if (job.getParameters() == null) return job;

        ObjectNode raw = job.getParameters().getRawData();
        if (raw == null) {
            logger.warn("No raw data present for jobId={}", job.getJobId());
            return job;
        }

        // Parse products array if present
        if (raw.has("products") && raw.get("products").isArray()) {
            Iterator<com.fasterxml.jackson.databind.JsonNode> it = raw.get("products").elements();
            while (it.hasNext()) {
                com.fasterxml.jackson.databind.JsonNode p = it.next();
                Product product = mapToProduct(p);
                // Deduplicate by productId
                try {
                    CompletableFuture<ObjectNode> existingFuture = entityService.getItem(
                        Product.ENTITY_NAME,
                        String.valueOf(Product.ENTITY_VERSION),
                        UUID.fromString(product.getTechnicalId())
                    );
                    ObjectNode existing = existingFuture.get();
                    boolean shouldPersist = existing == null || hasChanges(existing, product);
                    if (shouldPersist) {
                        // Upsert by adding new item - for prototype we always add to keep idempotency handled elsewhere
                        CompletableFuture<UUID> idFuture = entityService.addItem(Product.ENTITY_NAME, String.valueOf(Product.ENTITY_VERSION), product);
                        idFuture.get();
                        logger.info("Persisted product productId={} jobId={}", product.getProductId(), job.getJobId());
                    }
                } catch (Exception e) {
                    // If getItem fails assume not found and add
                    try {
                        CompletableFuture<UUID> idFuture = entityService.addItem(Product.ENTITY_NAME, String.valueOf(Product.ENTITY_VERSION), product);
                        idFuture.get();
                        logger.info("Persisted product productId={} jobId={} (created)", product.getProductId(), job.getJobId());
                    } catch (Exception ex) {
                        logger.error("Failed to persist product productId={} jobId={}", product.getProductId(), job.getJobId(), ex);
                    }
                }
            }
        }

        logger.info("TransformProcessor completed for jobId={}", job.getJobId());
        return job;
    }

    private Product mapToProduct(com.fasterxml.jackson.databind.JsonNode node) {
        Product p = new Product();
        if (node.has("productId")) p.setProductId(node.get("productId").asText());
        if (node.has("technicalId")) p.setTechnicalId(node.get("technicalId").asText());
        if (node.has("name")) p.setName(node.get("name").asText());
        if (node.has("category")) p.setCategory(node.get("category").asText());
        if (node.has("sku")) p.setSku(node.get("sku").asText());
        if (node.has("price")) p.setPrice(node.get("price").asDouble());
        if (node.has("stockLevel")) p.setStockLevel(node.get("stockLevel").asInt());
        if (node.has("reorderPoint")) p.setReorderPoint(node.get("reorderPoint").asInt());
        if (node.has("createdAt")) p.setCreatedAt(node.get("createdAt").asText());
        p.setLastUpdated(java.time.OffsetDateTime.now().toString());
        return p;
    }

    private boolean hasChanges(ObjectNode existing, Product product) {
        if (existing == null) return true;
        if (existing.has("price") && existing.get("price").asDouble() != product.getPrice()) return true;
        if (existing.has("stockLevel") && existing.get("stockLevel").asInt() != product.getStockLevel()) return true;
        if (existing.has("name") && !existing.get("name").asText().equals(product.getName())) return true;
        return false;
    }
}
