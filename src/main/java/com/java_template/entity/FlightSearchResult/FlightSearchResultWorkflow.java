package com.java_template.entity.FlightSearchResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
@RequiredArgsConstructor
public class FlightSearchResultWorkflow {

    private static final Logger logger = LoggerFactory.getLogger(FlightSearchResultWorkflow.class);
    private final ObjectMapper objectMapper;

    public CompletableFuture<ObjectNode> processFlightSearchResult(ObjectNode entity) {
        // Workflow orchestration only
        return processValidateInput(entity)
                .thenCompose(this::processCallExternalApi)
                .thenCompose(this::processParseResponse)
                .thenCompose(this::processApplyBusinessLogic)
                .thenCompose(this::processFinalizeResult);
    }

    private CompletableFuture<ObjectNode> processValidateInput(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Validating input entity: {}", entity);
            // Example validation, could add more detailed checks
            if (!entity.hasNonNull("departureAirport") || !entity.hasNonNull("arrivalAirport")) {
                entity.put("status", "error");
                entity.put("errorMessage", "Missing required airports");
            } else {
                entity.put("status", "validated");
            }
            return entity;
        });
    }

    private CompletableFuture<ObjectNode> processCallExternalApi(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Calling external API with entity: {}", entity);
            // TODO: Replace with actual async HTTP call to Airport Gap API
            // For now, simulate API response data by adding dummy flight info
            ObjectNode mockFlight = objectMapper.createObjectNode();
            mockFlight.put("airline", "MockAir");
            mockFlight.put("flightNumber", "MA1234");
            mockFlight.put("departureTime", "2023-12-01T08:00:00");
            mockFlight.put("arrivalTime", "2023-12-01T11:00:00");
            mockFlight.put("price", 350.0);
            entity.set("flights", objectMapper.createArrayNode().add(mockFlight));
            entity.put("status", "api_called");
            return entity;
        });
    }

    private CompletableFuture<ObjectNode> processParseResponse(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Parsing API response in entity: {}", entity);
            // Simulate parsing, here we assume flights are already set in entity
            if (!entity.has("flights") || entity.get("flights").isEmpty()) {
                entity.put("status", "no_flights_found");
            } else {
                entity.put("status", "flights_parsed");
            }
            return entity;
        });
    }

    private CompletableFuture<ObjectNode> processApplyBusinessLogic(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Applying business logic to entity: {}", entity);
            // Example business logic - mark entity with version
            entity.put("entityVersion", ENTITY_VERSION);
            entity.put("status", "business_logic_applied");
            // Example: Add a processed flag or modify prices if needed
            return entity;
        });
    }

    private CompletableFuture<ObjectNode> processFinalizeResult(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Finalizing result for entity: {}", entity);
            entity.put("status", "processed");
            return entity;
        });
    }
}