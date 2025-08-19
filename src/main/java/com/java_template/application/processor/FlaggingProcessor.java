package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.application.entity.extractionjob.version_1.ExtractionJob;
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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class FlaggingProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FlaggingProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public FlaggingProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Flagging for request: {}", request.getId());

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
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(Product.ENTITY_NAME, String.valueOf(Product.ENTITY_VERSION));
            ArrayNode items = itemsFuture.get();
            if (items == null || items.size() == 0) {
                logger.warn("No products found for flagging jobId={}", job.getJobId());
                return job;
            }

            Iterator<com.fasterxml.jackson.databind.JsonNode> it = items.elements();
            while (it.hasNext()) {
                ObjectNode node = (ObjectNode) it.next();
                Product p = new Product();
                if (node.has("productId")) p.setProductId(node.get("productId").asText());
                if (node.has("technicalId")) p.setTechnicalId(node.get("technicalId").asText());
                if (node.has("stockLevel")) p.setStockLevel(node.get("stockLevel").asInt());
                if (node.has("reorderPoint")) p.setReorderPoint(node.get("reorderPoint").isNull() ? null : node.get("reorderPoint").asInt());

                ArrayList<String> flags = new ArrayList<>();
                if (p.getReorderPoint() != null && p.getStockLevel() != null && p.getStockLevel() <= p.getReorderPoint()) {
                    flags.add("RESTOCK_CANDIDATE");
                }

                // Low performance if revenue metric exists and below threshold
                if (node.has("metrics") && node.get("metrics").has("revenue")) {
                    double revenue = node.get("metrics").get("revenue").asDouble(0.0);
                    if (revenue < 1.0) {
                        flags.add("LOW_PERFORMANCE");
                    }
                }

                if (!flags.isEmpty()) {
                    try {
                        p.setFlags(flags);
                        if (p.getTechnicalId() != null) {
                            CompletableFuture<UUID> updated = entityService.updateItem(Product.ENTITY_NAME, String.valueOf(Product.ENTITY_VERSION), UUID.fromString(p.getTechnicalId()), p);
                            updated.get();
                            logger.info("Updated flags for productId={} flags={} jobId={}", p.getProductId(), flags, job.getJobId());
                        } else {
                            logger.warn("Product technicalId missing for productId={} cannot update flags", p.getProductId());
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to update product flags productId={} : {}", p.getProductId(), e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error during flagging for jobId={}", job.getJobId(), e);
        }
        return job;
    }
}
