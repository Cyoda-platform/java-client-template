package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class Workflow {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public CompletableFuture<ObjectNode> processTradeExecutionEntity(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Workflow processTradeExecutionEntity started");

            // Step 1: Parse raw FpML XML and extract positions
            processParseFpml(entity).join();

            // Step 2: Enrich positions or entity if needed
            processEnrichPositions(entity).join();

            // Step 3: Validate entity state
            processValidateEntity(entity).join();

            log.info("Workflow processTradeExecutionEntity completed");
            return entity;
        });
    }

    private CompletableFuture<ObjectNode> processParseFpml(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("processParseFpml started");
            JsonNode rawFpmlXmlNode = entity.get("rawFpmlXml");
            if (rawFpmlXmlNode == null || rawFpmlXmlNode.isNull() || rawFpmlXmlNode.asText().isEmpty()) {
                throw new IllegalArgumentException("rawFpmlXml is missing or empty");
            }
            // Simulate parsing: create dummy positions array
            ArrayNode positionsArray = objectMapper.createArrayNode();
            ObjectNode position1 = objectMapper.createObjectNode();
            position1.put("positionId", UUID.randomUUID().toString());
            position1.put("instrument", "InterestRateSwap");
            position1.put("notional", 10_000_000L);
            position1.put("counterparty", "CounterpartyA");
            positionsArray.add(position1);
            entity.set("parsedPositions", positionsArray);
            log.info("processParseFpml completed with {} positions", positionsArray.size());
            return entity;
        });
    }

    private CompletableFuture<ObjectNode> processEnrichPositions(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("processEnrichPositions started");
            // TODO: Add enrichment logic if needed; currently no changes
            log.info("processEnrichPositions completed");
            return entity;
        });
    }

    private CompletableFuture<ObjectNode> processValidateEntity(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("processValidateEntity started");
            // Basic validation example
            JsonNode positions = entity.get("parsedPositions");
            if (positions == null || !positions.isArray() || positions.size() == 0) {
                throw new IllegalStateException("No parsedPositions found or empty");
            }
            log.info("processValidateEntity completed");
            return entity;
        });
    }
}