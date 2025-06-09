package com.java_template.entity.Booking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
public class BookingWorkflow {

    private static final Logger logger = LoggerFactory.getLogger(BookingWorkflow.class);
    private final ObjectMapper objectMapper;

    public BookingWorkflow(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // Orchestration method - only coordinates workflow steps, no business logic here
    public CompletableFuture<ObjectNode> processBooking(ObjectNode entity) {
        return processMarkWorkflowProcessed(entity)
                .thenCompose(this::processAttachSupplementaryData)
                .thenCompose(this::processCalculateTotals);
    }

    // Marks the entity as processed by the workflow
    private CompletableFuture<ObjectNode> processMarkWorkflowProcessed(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            entity.put("workflowProcessed", true);
            logger.info("Marked entity as workflowProcessed");
            return entity;
        });
    }

    // Attaches supplementary data fetched from some source
    private CompletableFuture<ObjectNode> processAttachSupplementaryData(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            JsonNode supplementaryData = fetchSupplementaryData();
            entity.set("supplementaryData", supplementaryData);
            logger.info("Attached supplementary data");
            return entity;
        });
    }

    // Example additional processing step: calculate totals or similar
    private CompletableFuture<ObjectNode> processCalculateTotals(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            // TODO: replace with actual calculation logic
            entity.put("totalRevenue", 5000);
            entity.put("averageBookingPrice", 250);
            entity.put("numberOfBookings", 20);
            logger.info("Calculated report totals");
            return entity;
        });
    }

    // Mock method to fetch supplementary data
    private JsonNode fetchSupplementaryData() {
        ObjectNode supplementaryData = objectMapper.createObjectNode();
        supplementaryData.put("info", "This is some supplementary data");
        return supplementaryData;
    }
}