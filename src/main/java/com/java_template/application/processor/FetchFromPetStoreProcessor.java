package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.extractionschedule.version_1.ExtractionSchedule;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.application.entity.salessnapshot.version_1.SalesSnapshot;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.util.Condition;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class FetchFromPetStoreProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(FetchFromPetStoreProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public FetchFromPetStoreProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Fetching data from PetStore for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(ExtractionSchedule.class)
            .validate(this::isValidEntity, "Invalid ExtractionSchedule state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ExtractionSchedule entity) {
        return entity != null && entity.isValid();
    }

    private ExtractionSchedule processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ExtractionSchedule> context) {
        ExtractionSchedule schedule = context.entity();
        ObjectMapper mapper = new ObjectMapper();
        HttpClient client = HttpClient.newHttpClient();
        try {
            // Call PetStore API to fetch available pets as a proxy for products
            URI uri = URI.create("https://petstore.swagger.io/v2/pet/findByStatus?status=available");
            HttpRequest req = HttpRequest.newBuilder(uri).GET().build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                logger.error("PetStore API returned non-200: {}", resp.statusCode());
                schedule.setStatus("FAILED");
                return schedule;
            }

            ArrayNode pets = (ArrayNode) mapper.readTree(resp.body());
            if (pets == null || pets.isEmpty()) {
                logger.info("No items returned from PetStore API for schedule {}", schedule.getSchedule_id());
            } else {
                Iterator<JsonNode> it = pets.elements();
                int processed = 0;
                while (it.hasNext()) {
                    JsonNode pet = it.next();
                    try {
                        // Map pet -> Product snapshot
                        Product snapshot = new Product();
                        // pet.id may be numeric
                        String productId = pet.has("id") ? pet.get("id").asText() : UUID.randomUUID().toString();
                        snapshot.setProduct_id(productId);
                        snapshot.setName(pet.has("name") && !pet.get("name").isNull() ? pet.get("name").asText() : "Unnamed");
                        String category = "Uncategorized";
                        if (pet.has("category") && pet.get("category") != null && pet.get("category").has("name")) {
                            category = pet.get("category").get("name").asText();
                        }
                        snapshot.setCategory(category);
                        // Prototype defaults for missing fields
                        snapshot.setPrice(19.99);
                        snapshot.setCost(10.00);
                        snapshot.setStock_level(20);
                        snapshot.setStore_id("petstore");

                        // Create a sales snapshot representing this extraction event
                        SalesSnapshot ss = new SalesSnapshot();
                        ss.setTimestamp(Instant.now().toString());
                        ss.setQuantity(1);
                        ss.setRevenue(snapshot.getPrice() != null ? snapshot.getPrice() : 0.0);
                        List<SalesSnapshot> history = new ArrayList<>();
                        history.add(ss);
                        snapshot.setSales_history(history);

                        // Persist/merge snapshot: try to find existing product by product_id + store_id
                        ArrayNode found = entityService.getItemsByCondition(
                            Product.ENTITY_NAME, String.valueOf(Product.ENTITY_VERSION),
                            SearchConditionRequest.group("AND",
                                Condition.of("$.product_id", "EQUALS", snapshot.getProduct_id()),
                                Condition.of("$.store_id", "EQUALS", snapshot.getStore_id())
                            ), true
                        ).get();

                        if (found != null && found.size() > 0) {
                            // Merge into first found entry
                            ObjectNode node = (ObjectNode) found.get(0);
                            Product existing = new Product();
                            existing.setProduct_id(node.has("product_id") ? node.get("product_id").asText() : snapshot.getProduct_id());
                            existing.setName(node.has("name") ? node.get("name").asText() : snapshot.getName());
                            existing.setCategory(node.has("category") ? node.get("category").asText() : snapshot.getCategory());
                            existing.setPrice(node.has("price") ? node.get("price").asDouble() : snapshot.getPrice());
                            existing.setCost(node.has("cost") ? node.get("cost").asDouble() : snapshot.getCost());
                            existing.setStore_id(node.has("store_id") ? node.get("store_id").asText() : snapshot.getStore_id());
                            existing.setStock_level(node.has("stock_level") ? node.get("stock_level").asInt() : snapshot.getStock_level());
                            // reconstruct sales_history if present
                            List<SalesSnapshot> existingHistory = new ArrayList<>();
                            if (node.has("sales_history") && node.get("sales_history`).isArray()) { /* defensive */ }
                            // parse sales_history if present
                            if (node.has("sales_history") && node.get("sales_history").isArray()) {
                                for (JsonNode hs : node.get("sales_history")) {
                                    try {
                                        SalesSnapshot s = new SalesSnapshot();
                                        s.setTimestamp(hs.has("timestamp") ? hs.get("timestamp").asText() : Instant.now().toString());
                                        s.setQuantity(hs.has("quantity") ? hs.get("quantity").asInt() : 0);
                                        s.setRevenue(hs.has("revenue") ? hs.get("revenue").asDouble() : 0.0);
                                        existingHistory.add(s);
                                    } catch (Exception ignore) {}
                                }
                            }
                            // append new snapshot and update stock
                            existingHistory.addAll(snapshot.getSales_history());
                            existing.setSales_history(existingHistory);
                            if (existing.getStock_level() != null) existing.setStock_level(Math.max(0, existing.getStock_level() - 1));

                            entityService.addItem(Product.ENTITY_NAME, String.valueOf(Product.ENTITY_VERSION), existing).get();
                            processed++;
                        } else {
                            // create new product entry
                            entityService.addItem(Product.ENTITY_NAME, String.valueOf(Product.ENTITY_VERSION), snapshot).get();
                            processed++;
                        }

                    } catch (Exception exInner) {
                        logger.error("Error processing pet node: {}", exInner.getMessage(), exInner);
                    }
                }
                logger.info("Processed {} items from PetStore for schedule {}", processed, schedule.getSchedule_id());
            }

            // Reschedule and set last_run on success
            schedule.setLast_run(Instant.now().toString());
            schedule.setStatus("COMPLETED");

            logger.info("Fetch completed for schedule {}. Marked COMPLETED", schedule.getSchedule_id());
        } catch (Exception ex) {
            logger.error("Error fetching from PetStore for schedule {}: {}", schedule.getSchedule_id(), ex.getMessage(), ex);
            schedule.setStatus("FAILED");
        }
        return schedule;
    }
}
