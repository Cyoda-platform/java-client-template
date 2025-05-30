package com.java_template.entity;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.Iterator;

@Slf4j
public class Workflow {

    public CompletableFuture<ObjectNode> processAnalyticsRequest(ObjectNode entity) {
        return processFilterPositions(entity)
                .thenCompose(this::processCalculateMetrics)
                .thenCompose(this::processCacheResults);
    }

    // Filters the trade positions based on filter criteria in the entity
    private CompletableFuture<ObjectNode> processFilterPositions(ObjectNode entity) {
        // Assume entity has "tradeExecutions" array and "filter" object
        if (!entity.has("tradeExecutions") || !entity.get("tradeExecutions").isArray()) {
            log.warn("No tradeExecutions array found in entity");
            entity.putArray("filteredPositions");
            return CompletableFuture.completedFuture(entity);
        }
        ArrayNode tradeExecutions = (ArrayNode) entity.get("tradeExecutions");
        ObjectNode filter = (ObjectNode) entity.get("filter");
        String filterCounterparty = filter != null && filter.has("counterparty") ? filter.get("counterparty").asText() : null;
        String filterInstrumentType = filter != null && filter.has("instrumentType") ? filter.get("instrumentType").asText() : null;

        ArrayNode filteredPositions = entity.putArray("filteredPositions");

        for (int i = 0; i < tradeExecutions.size(); i++) {
            ObjectNode tradeExecution = (ObjectNode) tradeExecutions.get(i);
            if (!tradeExecution.has("parsedPositions") || !tradeExecution.get("parsedPositions").isArray()) continue;
            ArrayNode positions = (ArrayNode) tradeExecution.get("parsedPositions");

            for (int j = 0; j < positions.size(); j++) {
                ObjectNode pos = (ObjectNode) positions.get(j);
                boolean matches = true;
                if (filterCounterparty != null && !filterCounterparty.equals(pos.path("counterparty").asText(null))) {
                    matches = false;
                }
                if (filterInstrumentType != null && !filterInstrumentType.equals(pos.path("instrument").asText(null))) {
                    matches = false;
                }
                if (matches) {
                    filteredPositions.add(pos);
                }
            }
        }
        return CompletableFuture.completedFuture(entity);
    }

    // Calculates requested metrics based on filteredPositions and metrics list in entity
    private CompletableFuture<ObjectNode> processCalculateMetrics(ObjectNode entity) {
        ArrayNode filteredPositions = (ArrayNode) entity.get("filteredPositions");
        if (filteredPositions == null) filteredPositions = entity.putArray("filteredPositions");

        ArrayNode metrics = (ArrayNode) entity.get("metrics");
        if (metrics == null) metrics = entity.putArray("metrics");

        long aggregateNotional = 0L;
        int positionCount = 0;

        positionCount = filteredPositions.size();

        for (int i = 0; i < filteredPositions.size(); i++) {
            ObjectNode pos = (ObjectNode) filteredPositions.get(i);
            aggregateNotional += pos.path("notional").asLong(0L);
        }

        ObjectNode results = entity.putObject("analyticsResults");
        // Clear previous results if any
        results.removeAll();

        for (int i = 0; i < metrics.size(); i++) {
            String metric = metrics.get(i).asText();
            switch (metric) {
                case "aggregateNotional":
                    results.put("aggregateNotional", aggregateNotional);
                    break;
                case "positionCount":
                    results.put("positionCount", positionCount);
                    break;
                default:
                    log.warn("Unknown metric requested: {}", metric);
            }
        }
        return CompletableFuture.completedFuture(entity);
    }

    // Caches or marks analytics results in the entity (for prototype just timestamp)
    private CompletableFuture<ObjectNode> processCacheResults(ObjectNode entity) {
        entity.put("lastAnalyticsRun", System.currentTimeMillis());
        return CompletableFuture.completedFuture(entity);
    }
}