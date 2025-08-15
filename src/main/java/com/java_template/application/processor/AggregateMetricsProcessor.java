package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class AggregateMetricsProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AggregateMetricsProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public AggregateMetricsProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Aggregating metrics for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Product.class)
            .validate(this::isValidEntity, "Invalid Product state")
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
        Product product = context.entity();
        try {
            // Load stored product records and try to find matching product by product_id + store_id
            CompletableFuture<ArrayNode> productsFuture = entityService.getItems(
                Product.ENTITY_NAME, String.valueOf(Product.ENTITY_VERSION)
            );
            ArrayNode items = productsFuture.get();
            Product stored = null;
            if (items != null) {
                Iterator<JsonNode> it = items.elements();
                while (it.hasNext()) {
                    ObjectNode node = (ObjectNode) it.next();
                    if (node.has("product_id") && node.get("product_id").asText().equals(product.getProduct_id())
                        && node.has("store_id") && node.get("store_id").asText().equals(product.getStore_id())) {
                        stored = new Product();
                        stored.setProduct_id(node.get("product_id").asText());
                        stored.setName(node.has("name") ? node.get("name").asText() : product.getName());
                        stored.setCategory(node.has("category") ? node.get("category").asText() : product.getCategory());
                        stored.setPrice(node.has("price") ? node.get("price").asDouble() : product.getPrice());
                        stored.setCost(node.has("cost") ? node.get("cost").asDouble() : product.getCost());
                        stored.setStock_level(node.has("stock_level") ? node.get("stock_level").asInt() : product.getStock_level());
                        stored.setStore_id(node.has("store_id") ? node.get("store_id").asText() : product.getStore_id());

                        // reconstruct sales_history if present
                        List<SalesSnapshot> existingHistory = new ArrayList<>();
                        if (node.has("sales_history") && node.get("sales_history").isArray()) {
                            for (JsonNode hs : node.get("sales_history")) {
                                try {
                                    SalesSnapshot s = new SalesSnapshot();
                                    s.setTimestamp(hs.has("timestamp") ? hs.get("timestamp").asText() : Instant.now().toString());
                                    s.setQuantity(hs.has("quantity") ? hs.get("quantity").asInt() : 0);
                                    s.setRevenue(hs.has("revenue") ? hs.get("revenue").asDouble() : 0.0);
                                    existingHistory.add(s);
                                } catch (Exception ignore) {
                                    // ignore malformed history entries
                                }
                            }
                        }
                        stored.setSales_history(existingHistory);
                        // parse tags if present
                        if (node.has("tags") && node.get("tags").isArray()) {
                            List<String> tags = new ArrayList<>();
                            for (JsonNode t : node.get("tags")) {
                                try { tags.add(t.asText()); } catch (Exception ignored) {}
                            }
                            stored.setTags(tags);
                        }
                        break;
                    }
                }
            }

            if (stored == null) {
                // Nothing to aggregate against; just persist incoming product snapshot as new product
                List<SalesSnapshot> history = product.getSales_history() != null ? product.getSales_history() : new ArrayList<>();
                product.setSales_history(history);
                CompletableFuture<UUID> idFuture = entityService.addItem(
                    Product.ENTITY_NAME, String.valueOf(Product.ENTITY_VERSION), product
                );
                logger.info("Persisted new product during aggregation");
                idFuture.get();
                return product;
            }

            // Merge sales_history and compute simple KPIs
            List<SalesSnapshot> merged = new ArrayList<>();
            if (stored.getSales_history() != null) merged.addAll(stored.getSales_history());
            if (product.getSales_history() != null) merged.addAll(product.getSales_history());

            // De-duplicate by (timestamp, quantity, revenue)
            List<SalesSnapshot> deduped = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (SalesSnapshot s : merged) {
                if (s == null) continue;
                String key = (s.getTimestamp() != null ? s.getTimestamp() : "") + "|" + (s.getQuantity()!=null?s.getQuantity():"") + "|" + (s.getRevenue()!=null?s.getRevenue():"");
                if (!seen.contains(key)) {
                    seen.add(key);
                    deduped.add(s);
                }
            }

            // Sort by timestamp ascending
            deduped.sort(Comparator.comparing(s -> s.getTimestamp() != null ? s.getTimestamp() : ""));

            // Compute total sales volume and revenue across merged
            int totalQty = 0;
            double totalRevenue = 0.0;
            for (SalesSnapshot s : deduped) {
                if (s == null) continue;
                if (s.getQuantity() != null) totalQty += s.getQuantity();
                if (s.getRevenue() != null) totalRevenue += s.getRevenue();
            }

            // Decide tags based on thresholds
            List<String> tags = stored.getTags() != null ? new ArrayList<>(stored.getTags()) : new ArrayList<>();
            if (totalQty < 5) {
                if (!tags.contains("underperformer")) tags.add("underperformer");
            } else {
                tags.remove("underperformer");
            }
            if (product.getStock_level() != null && product.getStock_level() < 10) {
                if (!tags.contains("restock_candidate")) tags.add("restock_candidate");
            } else {
                tags.remove("restock_candidate");
            }

            stored.setSales_history(deduped);
            stored.setTags(tags);
            // Update stock level to latest snapshot's value if present
            stored.setStock_level(product.getStock_level() != null ? product.getStock_level() : stored.getStock_level());

            // Persist updated product as a new entity record (prototype simplification)
            CompletableFuture<UUID> idFuture = entityService.addItem(
                Product.ENTITY_NAME, String.valueOf(Product.ENTITY_VERSION), stored
            );
            idFuture.get();
            logger.info("Aggregated metrics for product_id={}", product.getProduct_id());
            return stored;
        } catch (Exception ex) {
            logger.error("Error aggregating metrics for product {}: {}", product.getProduct_id(), ex.getMessage(), ex);
            return product;
        }
    }
}
