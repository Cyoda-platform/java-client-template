package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/cyoda-entity")
public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final EntityService entityService;

    public Workflow(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping("/data-extraction")
    public CompletableFuture<ObjectNode> processReportSummary(@RequestBody @Valid ObjectNode entity) {
        logger.info("Workflow processReportSummary started for reportDate={}", entity.get("reportDate").asText());

        return processFetchInventory(entity)
                .thenCompose(this::processFetchSalesData)
                .thenCompose(this::processAnalyzeKpis)
                .thenApply(this::processModifyEntity)
                .exceptionally(ex -> {
                    logger.error("Error in workflow processReportSummary", ex);
                    entity.put("workflowError", "Failed to process report summary: " + ex.getMessage());
                    return entity;
                });
    }

    private CompletableFuture<ObjectNode> processFetchInventory(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = "https://petstore.swagger.io/v2/store/inventory";
                String rawJson = restTemplate.getForObject(url, String.class);
                JsonNode inventoryNode = objectMapper.readTree(rawJson);
                entity.set("inventoryNode", inventoryNode);
            } catch (IOException e) {
                throw new RuntimeException("Failed to fetch or parse inventory data", e);
            }
            return entity;
        });
    }

    private CompletableFuture<ObjectNode> processFetchSalesData(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonNode salesData = getMockSalesData();
                entity.set("salesDataNode", salesData);
            } catch (IOException e) {
                throw new RuntimeException("Failed to fetch or parse sales data", e);
            }
            return entity;
        });
    }

    private CompletableFuture<ObjectNode> processAnalyzeKpis(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            JsonNode inventoryNode = entity.get("inventoryNode");
            JsonNode salesData = entity.get("salesDataNode");
            String reportDate = entity.get("reportDate").asText();

            ReportSummary summary = analyzeKpis(inventoryNode, salesData, reportDate);
            // Store summary as JSON in entity for next step
            ObjectNode summaryNode = objectMapper.valueToTree(summary);
            entity.set("summaryNode", summaryNode);

            // Clean up temp nodes
            entity.remove("inventoryNode");
            entity.remove("salesDataNode");

            return entity;
        });
    }

    private ObjectNode processModifyEntity(ObjectNode entity) {
        ObjectNode summaryNode = (ObjectNode) entity.get("summaryNode");
        if (summaryNode == null) {
            throw new RuntimeException("Missing summaryNode in entity");
        }

        entity.put("salesVolume", summaryNode.get("salesVolume").asInt());
        entity.put("revenue", summaryNode.get("revenue").asDouble());
        entity.put("inventoryTurnover", summaryNode.get("inventoryTurnover").asDouble());

        ArrayNode underperformingArray = objectMapper.createArrayNode();
        for (JsonNode p : summaryNode.withArray("underperformingProducts")) {
            ObjectNode productNode = objectMapper.createObjectNode();
            productNode.put("productId", p.get("productId").asText());
            productNode.put("reason", p.get("reason").asText());
            underperformingArray.add(productNode);
        }
        entity.set("underperformingProducts", underperformingArray);

        entity.remove("summaryNode");
        return entity;
    }

    private JsonNode getMockSalesData() throws IOException {
        String mockJson = """
                {
                  "products": [
                    {"productId": "p123", "salesVolume": 50, "revenue": 5000},
                    {"productId": "p456", "salesVolume": 5, "revenue": 200},
                    {"productId": "p789", "salesVolume": 100, "revenue": 15000}
                  ]
                }
                """;
        return objectMapper.readTree(mockJson);
    }

    private ReportSummary analyzeKpis(JsonNode inventoryNode, JsonNode salesData, String triggerDate) {
        List<UnderperformingProduct> underperforming = new java.util.ArrayList<>();
        salesData.get("products").forEach(p -> {
            int salesVolume = p.get("salesVolume").asInt();
            if (salesVolume < 10) {
                underperforming.add(new UnderperformingProduct(p.get("productId").asText(), "Low sales volume"));
            }
        });
        return new ReportSummary(
                triggerDate,
                155,
                17200.0,
                3.2,
                underperforming
        );
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportSummary {
        private String reportDate;
        private int salesVolume;
        private double revenue;
        private double inventoryTurnover;
        private List<UnderperformingProduct> underperformingProducts;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UnderperformingProduct {
        private String productId;
        private String reason;
    }
}