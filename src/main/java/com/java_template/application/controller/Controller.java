package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.ReportJob;
import com.java_template.application.entity.Report;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.net.HttpURLConnection;
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/entity")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // POST /entity/reportJob - create new ReportJob, trigger processing
    @PostMapping(path = "/reportJob", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createReportJob() {
        try {
            // Prepare new ReportJob entity for storage
            ReportJob newJob = new ReportJob();
            newJob.setRequestedAt(Instant.now());
            newJob.setStatus("PENDING");

            // Add new ReportJob via EntityService, get technicalId (UUID)
            CompletableFuture<UUID> idFuture = entityService.addItem("ReportJob", ENTITY_VERSION, newJob);
            UUID technicalId = idFuture.join();
            String technicalIdStr = technicalId.toString();
            newJob.setTechnicalId(technicalIdStr);

            logger.info("Created ReportJob with ID: {}", technicalIdStr);

            // After creation, update entity with technicalId for consistency if needed (skipped - no update allowed)
            // TODO: update ReportJob with technicalId if necessary (update operation not allowed currently)

            // Trigger event-driven processing synchronously here
            try {
                processReportJob(newJob);
            } catch (Exception e) {
                logger.error("Error processing ReportJob with ID: {}", technicalIdStr, e);
                newJob.setStatus("FAILED");
                // TODO: update ReportJob status to FAILED (update not allowed)
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Failed to process ReportJob"));
            }

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalIdStr));
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid argument creating ReportJob", ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            logger.error("Unexpected error creating ReportJob", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // GET /entity/reportJob/{id} - retrieve ReportJob by technicalId (UUID)
    @GetMapping(path = "/reportJob/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getReportJobById(@PathVariable("id") String id) {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem("ReportJob", ENTITY_VERSION, technicalId);
            ObjectNode node = itemFuture.join();
            if (node == null || node.isEmpty()) {
                logger.error("ReportJob not found with ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "ReportJob not found"));
            }
            // Deserialize ObjectNode to ReportJob entity for returning
            ReportJob job = objectMapper.treeToValue(node, ReportJob.class);
            return ResponseEntity.ok(job);
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid UUID format for ReportJob ID: {}", id, ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid UUID format"));
        } catch (Exception ex) {
            logger.error("Error retrieving ReportJob with ID: {}", id, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // GET /entity/report/{id} - retrieve Report by ReportJob technicalId (UUID)
    @GetMapping(path = "/report/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getReportByJobId(@PathVariable("id") String id) {
        try {
            // Use condition to filter reports by jobTechnicalId field equal to id (string)
            Condition condition = Condition.of("$.jobTechnicalId", "EQUALS", id);
            SearchConditionRequest searchCondition = SearchConditionRequest.group("AND", condition);

            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition("Report", ENTITY_VERSION, searchCondition, true);
            ArrayNode reportsNode = filteredItemsFuture.join();

            if (reportsNode == null || reportsNode.isEmpty()) {
                logger.error("Report not found for ReportJob ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Report not found"));
            }
            // Assuming one report per jobTechnicalId, return the first found
            ObjectNode reportNode = (ObjectNode) reportsNode.get(0);
            Report report = objectMapper.treeToValue(reportNode, Report.class);
            return ResponseEntity.ok(report);
        } catch (IllegalArgumentException ex) {
            logger.error("Invalid argument retrieving Report for ReportJob ID: {}", id, ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            logger.error("Error retrieving Report for ReportJob ID: {}", id, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // Process method for ReportJob entity - implement business logic workflow
    private void processReportJob(ReportJob entity) {
        logger.info("Processing ReportJob with ID: {}", entity.getTechnicalId());

        // Step 1: Fetch BTC/USD and BTC/EUR rates from CoinGecko API
        String apiUrl = "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd,eur";
        double btcUsdRate;
        double btcEurRate;
        try {
            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int status = conn.getResponseCode();
            if (status != 200) {
                throw new RuntimeException("Failed to fetch BTC rates, HTTP status: " + status);
            }
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder content = new StringBuilder();
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }
            in.close();
            conn.disconnect();

            String jsonResponse = content.toString();
            var root = objectMapper.readTree(jsonResponse);
            btcUsdRate = root.path("bitcoin").path("usd").asDouble();
            btcEurRate = root.path("bitcoin").path("eur").asDouble();

            if (btcUsdRate <= 0 || btcEurRate <= 0) {
                throw new RuntimeException("Invalid BTC rates retrieved");
            }
        } catch (Exception e) {
            logger.error("Error fetching BTC conversion rates for ReportJob ID: {}", entity.getTechnicalId(), e);
            entity.setStatus("FAILED");
            // TODO: update ReportJob status to FAILED (update not allowed)
            throw new RuntimeException("Failed to fetch BTC conversion rates");
        }

        // Step 2: Save Report entity with fetched rates
        Report report = new Report();
        report.setJobTechnicalId(entity.getTechnicalId());
        report.setGeneratedAt(Instant.now());
        report.setBtcUsdRate(btcUsdRate);
        report.setBtcEurRate(btcEurRate);
        report.setEmailSent(false);

        // Add Report entity via EntityService
        try {
            CompletableFuture<UUID> reportIdFuture = entityService.addItem("Report", ENTITY_VERSION, report);
            UUID reportId = reportIdFuture.join();
            String reportIdStr = reportId.toString();
            // Use same technicalId as ReportJob for simplicity is not possible here because they are UUIDs from external service
            // TODO: if needed, link Report technicalId with ReportJob technicalId externally

        } catch (Exception e) {
            logger.error("Failed to save Report entity for ReportJob ID: {}", entity.getTechnicalId(), e);
            entity.setStatus("FAILED");
            // TODO: update ReportJob status to FAILED (update not allowed)
            throw new RuntimeException("Failed to save Report entity");
        }

        // Step 3: Update ReportJob with fetched rates and status
        entity.setBtcUsdRate(btcUsdRate);
        entity.setBtcEurRate(btcEurRate);
        entity.setStatus("FETCHED");
        // TODO: update ReportJob entity with new data (update not allowed)

        // Step 4: Send email with conversion rates (simulate email sending)
        try {
            sendEmailReport(entity, report);
            entity.setStatus("SENT");
            entity.setEmailSentAt(Instant.now());
            report.setEmailSent(true);
            // TODO: update Report and ReportJob entities with email sent status (update not allowed)
            logger.info("Email sent successfully for ReportJob ID: {}", entity.getTechnicalId());
        } catch (Exception e) {
            logger.error("Failed to send email for ReportJob ID: {}", entity.getTechnicalId(), e);
            entity.setStatus("FAILED");
            // TODO: update ReportJob status to FAILED (update not allowed)
            throw new RuntimeException("Failed to send email report");
        }
    }

    // Simulated email sending method with logging
    private void sendEmailReport(ReportJob job, Report report) {
        String emailContent = String.format(
            "Bitcoin Conversion Rates Report\nRequested At: %s\nBTC/USD: %.4f\nBTC/EUR: %.4f",
            job.getRequestedAt().toString(),
            report.getBtcUsdRate(),
            report.getBtcEurRate()
        );
        logger.info("Sending email report for ReportJob ID: {}\n{}", job.getTechnicalId(), emailContent);
        // Real email integration to be implemented
    }
}