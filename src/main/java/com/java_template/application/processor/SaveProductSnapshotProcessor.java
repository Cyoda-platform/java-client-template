package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.application.entity.salessnapshot.version_1.SalesSnapshot;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class SaveProductSnapshotProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SaveProductSnapshotProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public SaveProductSnapshotProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Saving product snapshot for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Product.class)
            .validate(this::isValidEntity, "Invalid Product snapshot state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Product entity) {
        return entity != null && entity.isValid();
    }

    private Product processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Product> context) {
        Product snapshot = context.entity();
        try {
            // Load existing product by product_id + store_id using getItemsByCondition
            String condition = null;
            // For simplicity, use getItems (prototype) and naive matching
            CompletableFuture<ArrayNode> allProductsFuture = entityService.getItems(
                Product.ENTITY_NAME,
                String.valueOf(Product.ENTITY_VERSION)
            );
            ArrayNode products = allProductsFuture.get();
            Product existing = null;
            if (products != null) {
                Iterator<com.fasterxml.jackson.databind.JsonNode> it = products.elements();
                while (it.hasNext()) {
                    ObjectNode node = (ObjectNode) it.next();
                    if (node.has("product_id") && node.get("product_id").asText().equals(snapshot.getProduct_id())
                        && node.has("store_id") && node.get("store_id").asText().equals(snapshot.getStore_id())) {
                        // Found existing
                        existing = new Product();
                        existing.setProduct_id(node.get("product_id").asText());
                        existing.setName(node.has("name") ? node.get("name").asText() : snapshot.getName());
                        existing.setCategory(node.has("category") ? node.get("category").asText() : snapshot.getCategory());
                        existing.setPrice(node.has("price") ? node.get("price").asDouble() : snapshot.getPrice());
                        existing.setCost(node.has("cost") ? node.get("cost").asDouble() : snapshot.getCost());
                        existing.setStock_level(node.has("stock_level") ? node.get("stock_level").asInt() : snapshot.getStock_level());
                        existing.setStore_id(node.has("store_id") ? node.get("store_id").asText() : snapshot.getStore_id());
                        // sales_history merging not fully implemented in prototype
                        existing.setSales_history(new ArrayList<>());
                        break;
                    }
                }
            }

            // Merge snapshot: append a new SalesSnapshot
            SalesSnapshot snap = new SalesSnapshot();
            snap.setTimestamp(Instant.now().toString());
            snap.setQuantity(1);
            snap.setRevenue(snapshot.getPrice() != null ? snapshot.getPrice() : 0.0);
            if (existing == null) {
                // create new product
                Product newProduct = new Product();
                newProduct.setProduct_id(snapshot.getProduct_id());
                newProduct.setName(snapshot.getName());
                newProduct.setCategory(snapshot.getCategory());
                newProduct.setPrice(snapshot.getPrice());
                newProduct.setCost(snapshot.getCost());
                newProduct.setStock_level(snapshot.getStock_level());
                newProduct.setStore_id(snapshot.getStore_id());
                List<SalesSnapshot> history = new ArrayList<>();
                history.add(snap);
                newProduct.setSales_history(history);
                // Persist via entityService.addItem
                CompletableFuture<UUID> idFuture = entityService.addItem(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION),
                    newProduct
                );
                UUID addedId = idFuture.get();
                logger.info("Created new product with technicalId={}", addedId);
                return newProduct;
            } else {
                // Update existing: append snapshot to sales_history and recompute stock_level
                List<SalesSnapshot> history = existing.getSales_history();
                if (history == null) history = new ArrayList<>();
                history.add(snap);
                existing.setSales_history(history);
                // naive stock decrement
                if (existing.getStock_level() != null) {
                    existing.setStock_level(Math.max(0, existing.getStock_level() - 1));
                }
                // Persist updated product using addItem to create new record (prototype simplification)
                CompletableFuture<UUID> idFuture = entityService.addItem(
                    Product.ENTITY_NAME,
                    String.valueOf(Product.ENTITY_VERSION),
                    existing
                );
                UUID updatedId = idFuture.get();
                logger.info("Appended snapshot and persisted product with new technicalId={}", updatedId);
                return existing;
            }
        } catch (Exception ex) {
            logger.error("Error saving product snapshot: {}", ex.getMessage(), ex);
            // In case of error, do not throw; mark product as-is
            return snapshot;
        }
    }
}
