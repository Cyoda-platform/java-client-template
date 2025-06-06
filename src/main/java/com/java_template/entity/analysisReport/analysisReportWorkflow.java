package com.java_template.entity.analysisReport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.*;

@Component
public class analysisReportWorkflow {

    private final ObjectMapper objectMapper;

    public analysisReportWorkflow(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CompletableFuture<ObjectNode> processAnalysisReport(ObjectNode entity) {
        return processDownloadData(entity)
                .thenCompose(this::processPerformAnalysis)
                .thenCompose(this::processPrepareReport)
                .thenApply(e -> {
                    // workflow orchestration only, no business logic here
                    e.put("state", "completed");
                    return e;
                });
    }

    private CompletableFuture<ObjectNode> processDownloadData(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String csvUrl = entity.has("url") && !entity.get("url").asText().isEmpty()
                        ? entity.get("url").asText()
                        : DEFAULT_CSV_URL;

                // Download CSV data as a list of maps
                List<Map<String, String>> csvData = CsvUtils.downloadCsv(csvUrl);

                // Store csvData as a nested JSON array in entity
                entity.set("csvData", objectMapper.valueToTree(csvData));

                entity.put("state", "data_downloaded");
            } catch (Exception ex) {
                entity.put("state", "failed");
                entity.put("error", ex.getMessage());
            }
            return entity;
        });
    }

    private CompletableFuture<ObjectNode> processPerformAnalysis(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            if (!entity.has("csvData")) {
                entity.put("state", "failed");
                entity.put("error", "No CSV data found");
                return entity;
            }
            List<Map<String, String>> csvData = objectMapper.convertValue(
                    entity.get("csvData"), objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));

            // Perform summary analysis on price column
            List<Double> prices = csvData.stream()
                    .map(row -> row.get("price"))
                    .filter(s -> s != null)
                    .map(s -> {
                        try {
                            return Double.parseDouble(s);
                        } catch (NumberFormatException e) {
                            return null;
                        }
                    })
                    .filter(d -> d != null)
                    .collect(Collectors.toList());

            double mean = prices.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double median = calculateMedian(prices);
            int total = csvData.size();

            ObjectNode summaryStats = objectMapper.createObjectNode();
            summaryStats.put("meanPrice", mean);
            summaryStats.put("medianPrice", median);
            summaryStats.put("totalListings", total);

            entity.set("summaryStatistics", summaryStats);
            entity.put("state", "analysis_completed");

            return entity;
        });
    }

    private CompletableFuture<ObjectNode> processPrepareReport(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            // Prepare the final report object in entity, for example add timestamp
            entity.put("generatedAt", System.currentTimeMillis());
            entity.put("state", "report_prepared");
            return entity;
        });
    }

    // Helper method to calculate median
    private double calculateMedian(List<Double> values) {
        if (values.isEmpty()) return 0;
        List<Double> sorted = values.stream().sorted().collect(Collectors.toList());
        int mid = sorted.size() / 2;
        if (sorted.size() % 2 == 0) {
            return (sorted.get(mid - 1) + sorted.get(mid)) / 2.0;
        } else {
            return sorted.get(mid);
        }
    }
}