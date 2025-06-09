package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/cyoda/api")
public class CyodaEntityControllerPrototype {

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    // Workflow function for processing retrieve data entity
    private CompletableFuture<JsonNode> processRetrieveData(ObjectNode entityData) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Processing RetrieveData entity asynchronously: {}", entityData);
            try {
                Thread.sleep(2000); // Simulate processing time
                entityData.put("status", "processed");
                logger.info("RetrieveData entity processed: {}", entityData);
            } catch (InterruptedException e) {
                logger.error("Error processing RetrieveData entity", e);
            }
            return entityData;
        });
    }

    // Workflow function for processing analysis entity
    private CompletableFuture<JsonNode> processAnalysis(ObjectNode entityData) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Processing Analysis entity asynchronously: {}", entityData);
            // Simulate analysis processing logic
            entityData.put("analysisCompleted", true);
            logger.info("Analysis entity processed: {}", entityData);
            return entityData;
        });
    }

    // Workflow function for processing report entity
    private CompletableFuture<JsonNode> processReport(ObjectNode entityData) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Processing Report entity asynchronously: {}", entityData);
            // Simulate report generation logic
            entityData.put("reportGenerated", true);
            logger.info("Report entity processed: {}", entityData);
            return entityData;
        });
    }

    @PostMapping("/data/retrieve")
    public CompletableFuture<Map<String, Object>> retrieveData(@RequestBody @Valid RetrieveDataRequest request) {
        logger.info("Initiating data retrieval for date: {}", request.getDate());
        ObjectNode data = objectMapper.createObjectNode();
        data.put("date", request.getDate());
        return entityService.addItem(
                "RetrieveData",
                ENTITY_VERSION,
                data,
                this::processRetrieveData
        ).thenApply(id -> Map.of(
                "status", "success",
                "message", "Data retrieval job initiated",
                "entityId", id.toString()
        ));
    }

    @PostMapping("/analysis/perform")
    public CompletableFuture<Map<String, Object>> performAnalysis(@RequestBody @Valid AnalysisRequest request) {
        logger.info("Initiating analysis on data: {}", request.getData());
        ObjectNode analysisData = objectMapper.createObjectNode();
        analysisData.set("data", request.getData());
        return entityService.addItem(
                "Analysis",
                ENTITY_VERSION,
                analysisData,
                this::processAnalysis
        ).thenApply(id -> Map.of(
                "status", "success",
                "message", "Analysis job initiated",
                "entityId", id.toString()
        ));
    }

    @PostMapping("/report/generate")
    public CompletableFuture<Map<String, Object>> generateReport(@RequestBody @Valid ReportRequest request) {
        logger.info("Initiating report generation based on analysis: {}", request.getAnalysis());
        ObjectNode reportData = objectMapper.createObjectNode();
        reportData.set("analysis", request.getAnalysis());
        return entityService.addItem(
                "Report",
                ENTITY_VERSION,
                reportData,
                this::processReport
        ).thenApply(id -> Map.of(
                "status", "success",
                "message", "Report generation job initiated",
                "entityId", id.toString()
        ));
    }

    @GetMapping("/report/latest")
    public Map<String, Object> getLatestReport() {
        logger.info("Fetching latest report");
        String reportUrl = "http://example.com/report/latest";
        return Map.of(
                "status", "success",
                "reportUrl", reportUrl
        );
    }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, String> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Error occurred: {}", ex.getStatusCode().toString(), ex);
        return Map.of("error", ex.getStatusCode().toString());
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class RetrieveDataRequest {
        @NotBlank
        private String date;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class AnalysisRequest {
        @NotNull
        private JsonNode data;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class ReportRequest {
        @NotNull
        private JsonNode analysis;
    }
}