package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import com.java_template.application.entity.ReportJob;
import com.java_template.application.entity.Report;
import java.time.Instant;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, ReportJob> reportJobCache = new ConcurrentHashMap<>();
    private final AtomicLong reportJobIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, Report> reportCache = new ConcurrentHashMap<>();
    private final AtomicLong reportIdCounter = new AtomicLong(1);

    private final ObjectMapper objectMapper = new ObjectMapper();

    // POST /prototype/reportJob - create new ReportJob, trigger processing
    @PostMapping(path = "/reportJob", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> createReportJob() {
        // Generate unique technicalId as string of incremented long
        String technicalId = String.valueOf(reportJobIdCounter.getAndIncrement());

        ReportJob newJob = new ReportJob();
        newJob.setTechnicalId(technicalId);
        newJob.setRequestedAt(Instant.now());
        newJob.setStatus("PENDING");

        reportJobCache.put(technicalId, newJob);

        log.info("Created ReportJob with ID: {}", technicalId);

        // Trigger event-driven processing asynchronously or synchronously here (for prototype synchronously)
        try {
            processReportJob(newJob);
        } catch (Exception e) {
            log.error("Error processing ReportJob with ID: {}", technicalId, e);
            newJob.setStatus("FAILED");
            reportJobCache.put(technicalId, newJob);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Failed to process ReportJob"));
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId));
    }

    // GET /prototype/reportJob/{id} - retrieve ReportJob by technicalId
    @GetMapping(path = "/reportJob/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getReportJobById(@PathVariable("id") String id) {
        ReportJob job = reportJobCache.get(id);
        if (job == null) {
            log.error("ReportJob not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "ReportJob not found"));
        }
        return ResponseEntity.ok(job);
    }

    // GET /prototype/report/{id} - retrieve Report by ReportJob technicalId
    @GetMapping(path = "/report/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getReportByJobId(@PathVariable("id") String id) {
        Report report = reportCache.get(id);
        if (report == null) {
            log.error("Report not found for ReportJob ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Report not found"));
        }
        return ResponseEntity.ok(report);
    }

    // Process method for ReportJob entity - implement business logic workflow
    private void processReportJob(ReportJob entity) {
        log.info("Processing ReportJob with ID: {}", entity.getTechnicalId());

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
            JsonNode root = objectMapper.readTree(jsonResponse);
            btcUsdRate = root.path("bitcoin").path("usd").asDouble();
            btcEurRate = root.path("bitcoin").path("eur").asDouble();

            if (btcUsdRate <= 0 || btcEurRate <= 0) {
                throw new RuntimeException("Invalid BTC rates retrieved");
            }
        } catch (Exception e) {
            log.error("Error fetching BTC conversion rates for ReportJob ID: {}", entity.getTechnicalId(), e);
            entity.setStatus("FAILED");
            reportJobCache.put(entity.getTechnicalId(), entity);
            throw new RuntimeException("Failed to fetch BTC conversion rates");
        }

        // Step 2: Save Report entity with fetched rates
        Report report = new Report();
        report.setJobTechnicalId(entity.getTechnicalId());
        report.setGeneratedAt(Instant.now());
        report.setBtcUsdRate(btcUsdRate);
        report.setBtcEurRate(btcEurRate);
        report.setEmailSent(false);

        String reportId = entity.getTechnicalId(); // Use same technicalId for simplicity
        reportCache.put(reportId, report);

        // Step 3: Update ReportJob with fetched rates and status
        entity.setBtcUsdRate(btcUsdRate);
        entity.setBtcEurRate(btcEurRate);
        entity.setStatus("FETCHED");
        reportJobCache.put(entity.getTechnicalId(), entity);

        // Step 4: Send email with conversion rates (simulate email sending)
        try {
            sendEmailReport(entity, report);
            entity.setStatus("SENT");
            entity.setEmailSentAt(Instant.now());
            report.setEmailSent(true);
            reportCache.put(reportId, report);
            reportJobCache.put(entity.getTechnicalId(), entity);
            log.info("Email sent successfully for ReportJob ID: {}", entity.getTechnicalId());
        } catch (Exception e) {
            log.error("Failed to send email for ReportJob ID: {}", entity.getTechnicalId(), e);
            entity.setStatus("FAILED");
            reportJobCache.put(entity.getTechnicalId(), entity);
            throw new RuntimeException("Failed to send email report");
        }
    }

    // Simulated email sending method with logging
    private void sendEmailReport(ReportJob job, Report report) {
        // Compose email content
        String emailContent = String.format(
            "Bitcoin Conversion Rates Report\nRequested At: %s\nBTC/USD: %.4f\nBTC/EUR: %.4f",
            job.getRequestedAt().toString(),
            report.getBtcUsdRate(),
            report.getBtcEurRate()
        );
        // Simulate sending email by logging
        log.info("Sending email report for ReportJob ID: {}\n{}", job.getTechnicalId(), emailContent);
        // In real implementation, integrate with Spring Boot Mail or SMTP here
    }
}