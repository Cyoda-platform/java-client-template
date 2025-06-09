package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
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
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@Slf4j
@RestController
@RequestMapping("/cyoda/entity")
@Validated
public class CyodaEntityControllerPrototype {

    private final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EntityService entityService;

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping("/users/fetch")
    public ResponseEntity<String> fetchAndStoreUserData(@RequestBody @Valid FetchRequest request) {
        try {
            String response = new RestTemplate().getForObject(request.getApiUrl(), String.class);
            JsonNode users = objectMapper.readTree(response);

            users.forEach(user -> {
                CompletableFuture<UUID> idFuture = entityService.addItem(
                    "User",
                    ENTITY_VERSION,
                    user,
                    this::processUser
                );
                logger.info("User data stored with technicalId: {}", idFuture.join());
            });

            logger.info("Data fetched and stored successfully");
            return ResponseEntity.ok("Data fetched and stored successfully");
        } catch (Exception e) {
            logger.error("Error fetching data", e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Error fetching data", e);
        }
    }

    @PostMapping("/reports/generate")
    public ResponseEntity<ReportResponse> generateMonthlyReport(@RequestBody @Valid ReportRequest request) {
        String reportId = "report-" + request.getMonth();

        ObjectNode reportData = objectMapper.createObjectNode();
        reportData.put("reportId", reportId);
        reportData.put("content", "Report content for " + request.getMonth());

        CompletableFuture<UUID> reportFuture = entityService.addItem(
            "Report",
            ENTITY_VERSION,
            reportData,
            this::processReport
        );

        logger.info("Report generated with technicalId: {}", reportFuture.join());

        return ResponseEntity.ok(new ReportResponse("success", reportId));
    }

    @GetMapping("/reports/{reportId}")
    public ResponseEntity<ReportData> retrieveReport(@PathVariable String reportId) {
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
            Condition.of("$.reportId", "EQUALS", reportId)
        );

        CompletableFuture<ArrayNode> reportsFuture = entityService.getItemsByCondition(
            "Report",
            ENTITY_VERSION,
            condition
        );

        ArrayNode reports = reportsFuture.join();
        if (reports.size() > 0) {
            ObjectNode report = (ObjectNode) reports.get(0);
            return ResponseEntity.ok(objectMapper.convertValue(report, ReportData.class));
        } else {
            logger.error("Report not found: {}", reportId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found");
        }
    }

    @PostMapping("/reports/send")
    public ResponseEntity<String> sendReportToAdmin(@RequestBody @Valid SendReportRequest request) {
        logger.info("Report {} sent to admin email: {}", request.getReportId(), request.getAdminEmail());
        return ResponseEntity.ok("Report sent to admin email successfully");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> handleResponseStatusException(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(ex.getStatusCode().toString());
    }

    private CompletableFuture<JsonNode> processUser(JsonNode user) {
        return CompletableFuture.supplyAsync(() -> {
            ((ObjectNode) user).put("processed", true);
            logger.info("Processed user data: {}", user);
            return user;
        });
    }

    private CompletableFuture<JsonNode> processReport(JsonNode report) {
        return CompletableFuture.supplyAsync(() -> {
            ((ObjectNode) report).put("processed", true);
            logger.info("Processed report data: {}", report);
            return report;
        });
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class UserData {
        private String userId;
        private String data;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class ReportData {
        private String reportId;
        private String content;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class ReportResponse {
        private String status;
        private String reportId;
    }

    @Data
    @NoArgsConstructor
    static class FetchRequest {
        @NotBlank
        private String apiUrl;
    }

    @Data
    @NoArgsConstructor
    static class ReportRequest {
        @NotBlank
        @Pattern(regexp = "\\d{4}-\\d{2}", message = "Month should be in the format yyyy-MM")
        private String month;
    }

    @Data
    @NoArgsConstructor
    static class SendReportRequest {
        @NotBlank
        private String reportId;
        @NotBlank
        @Pattern(regexp = ".+@.+\\..+", message = "Email should be valid")
        private String adminEmail;
    }
}