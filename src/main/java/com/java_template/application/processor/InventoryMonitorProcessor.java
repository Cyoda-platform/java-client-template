package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class InventoryMonitorProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(InventoryMonitorProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public InventoryMonitorProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing InventoryMonitor for request: {}", request.getId());

        // Scan all products and mark inactive if inventory is 0
        try {
            CompletableFuture<ArrayNode> productsFuture = entityService.getItems(Product.ENTITY_NAME, String.valueOf(Product.ENTITY_VERSION));
            ArrayNode products = productsFuture.get();
            if (products != null) {
                for (int i = 0; i < products.size(); i++) {
                    ObjectNode node = (ObjectNode) products.get(i);
                    Product p = objectMapper.convertValue(node, Product.class);
                    if (p.getInventory() != null && p.getInventory() == 0 && Boolean.TRUE.equals(p.getActive())) {
                        p.setActive(false);
                        if (node.has("technicalId")) {
                            try {
                                entityService.updateItem(Product.ENTITY_NAME, String.valueOf(Product.ENTITY_VERSION), java.util.UUID.fromString(node.get("technicalId").asText()), p).get();
                                logger.info("Product {} deactivated due to zero inventory", p.getProductId());
                            } catch (Exception ex) {
                                logger.warn("Failed to update product active flag for {}", p.getProductId(), ex);
                            }
                        }
                    }
                }
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error while monitoring inventory", e);
        }

        return serializer.withRequest(request).toEntity(Product.class).map(ctx -> ctx.entity()).complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }
}
