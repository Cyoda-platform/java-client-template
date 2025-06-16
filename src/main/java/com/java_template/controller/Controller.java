package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/cyoda-activities")
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private static final String ENTITY_NAME = "ActivityReport";

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostConstruct
    public void init() {
        logger.info("CyodaEntityControllerPrototype initialized");
    }

    // Endpoint triggers ingestion by adding minimal entity without workflow
    @PostMapping("/ingest")
    public ResponseEntity<GenericResponse> ingestActivities(@RequestBody @Valid IngestRequest ingestRequest) {
        String dateStr = ingestRequest.getDate() != null ? ingestRequest.getDate() : LocalDate.now().toString();
        logger.info("Received ingestion request for date {}", dateStr);

        ObjectNode initialEntity = objectMapper.createObjectNode();
        initialEntity.put("date", dateStr);

        // Add item without workflow argument
        CompletableFuture<UUID> addFuture = entityService.addItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                initialEntity
        );

        // Return immediately
        return ResponseEntity.ok(new GenericResponse("success", "Data ingestion and processing started for date " + dateStr));
    }

    // Retrieve ActivityReport by date
    @GetMapping("/report")
    public ResponseEntity<ActivityReport> getReport(
            @RequestParam @NotBlank @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$") String date) {
        logger.info("Fetching report for date {}", date);

        String condition = String.format("{\"date\":\"%s\"}", date);
        CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, condition);

        ArrayNode resultArray = filteredItemsFuture.join();
        if (resultArray == null || resultArray.isEmpty()) {
            logger.warn("Report not found for date {}", date);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found for date " + date);
        }

        ObjectNode objNode = (ObjectNode) resultArray.get(0);
        try {
            ActivityReport report = objectMapper.treeToValue(objNode, ActivityReport.class);
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            logger.error("Failed to parse ActivityReport object: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to parse report data");
        }
    }

    // Endpoint to request sending report email; actual email sending is done asynchronously via secondary entity
    @PostMapping("/report/send")
    public ResponseEntity<GenericResponse> sendReportEmail(@RequestBody @Valid SendReportRequest request) {
        String date = request.getDate();
        String adminEmail = request.getAdminEmail();
        logger.info("Request to send report for date {} to admin {}", date, adminEmail);

        String condition = String.format("{\"date\":\"%s\"}", date);
        CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, condition);

        ArrayNode resultArray = filteredItemsFuture.join();
        if (resultArray == null || resultArray.isEmpty()) {
            logger.warn("Report not found for date {}", date);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found for date " + date);
        }

        // Create EmailJob entity asynchronously to trigger email sending workflow later
        ObjectNode emailJobEntity = objectMapper.createObjectNode();
        emailJobEntity.put("emailTo", adminEmail);
        emailJobEntity.put("subject", "Requested Activity Report for " + date);
        emailJobEntity.put("body", "The activity report for date " + date + " has been requested.");
        emailJobEntity.put("createdAt", OffsetDateTime.now().toString());
        emailJobEntity.put("status", "pending");
        emailJobEntity.put("reportDate", date);

        // Add EmailJob entity with no workflow (identity function)
        entityService.addItem("EmailJob", "1.0", emailJobEntity, Function.identity());

        return ResponseEntity.ok(new GenericResponse("success", "Report send request received for " + date + " to " + adminEmail));
    }

    // DTOs

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IngestRequest {
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$")
        private String date;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SendReportRequest {
        @NotBlank
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$")
        private String date;
        @NotBlank
        @Email
        private String adminEmail;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GenericResponse {
        private String status;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivityReport {
        private String date;
        private int totalActivities;
        private Map<String, Integer> activityTypes;
        private Map<String, String> trends;
        private String[] anomalies;
    }

}