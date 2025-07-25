package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.net.URI;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, ReportJob> reportJobCache = new ConcurrentHashMap<>();
    private final AtomicLong reportJobIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, ConversionReport> conversionReportCache = new ConcurrentHashMap<>();
    private final AtomicLong conversionReportIdCounter = new AtomicLong(1);

    private final RestTemplate restTemplate = new RestTemplate();

    // POST /prototype/reportJob - create ReportJob and trigger processing
    @PostMapping("/reportJob")
    public ResponseEntity<Map<String, String>> createReportJob(@RequestBody Map<String, String> request) {
        String recipientEmail = request.get("recipientEmail");
        if (recipientEmail == null || recipientEmail.isBlank()) {
            log.error("recipientEmail is missing or blank");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "recipientEmail is required"));
        }
        String technicalId = Long.toString(reportJobIdCounter.getAndIncrement());
        ReportJob job = new ReportJob();
        job.setId(technicalId);
        job.setRequestTimestamp(LocalDateTime.now());
        job.setStatus("PENDING");
        job.setRecipientEmail(recipientEmail);
        job.setErrorMessage(null);

        reportJobCache.put(technicalId, job);
        log.info("Created ReportJob with ID: {}", technicalId);

        // Trigger processing asynchronously (simulated here synchronously)
        processReportJob(job);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId));
    }

    // GET /prototype/reportJob/{id} - get ReportJob by technicalId
    @GetMapping("/reportJob/{id}")
    public ResponseEntity<?> getReportJob(@PathVariable("id") String id) {
        ReportJob job = reportJobCache.get(id);
        if (job == null) {
            log.error("ReportJob with ID {} not found", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "ReportJob not found"));
        }
        return ResponseEntity.ok(job);
    }

    // GET /prototype/conversionReport/{jobTechnicalId} - get ConversionReport by jobTechnicalId
    @GetMapping("/conversionReport/{jobTechnicalId}")
    public ResponseEntity<?> getConversionReport(@PathVariable("jobTechnicalId") String jobTechnicalId) {
        ConversionReport report = conversionReportCache.get(jobTechnicalId);
        if (report == null) {
            log.error("ConversionReport for Job ID {} not found", jobTechnicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "ConversionReport not found"));
        }
        return ResponseEntity.ok(report);
    }

    private void processReportJob(ReportJob job) {
        log.info("Processing ReportJob with ID: {}", job.getId());
        if (job.getRecipientEmail() == null || job.getRecipientEmail().isBlank()) {
            log.error("Recipient email is blank for ReportJob ID: {}", job.getId());
            job.setStatus("FAILED");
            job.setErrorMessage("Recipient email is missing or blank");
            reportJobCache.put(job.getId(), job);
            return;
        }

        job.setStatus("FETCHING");
        reportJobCache.put(job.getId(), job);

        try {
            // Fetch BTC/USD and BTC/EUR rates from CoinGecko API
            String url = "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd,eur";
            Map<String, Map<String, Double>> response = restTemplate.getForObject(url, Map.class);
            if (response == null || !response.containsKey("bitcoin")) {
                throw new RuntimeException("Invalid response from BTC price API");
            }
            Map<String, Double> btcPrices = response.get("bitcoin");
            Double btcUsd = btcPrices.get("usd");
            Double btcEur = btcPrices.get("eur");
            if (btcUsd == null || btcEur == null) {
                throw new RuntimeException("BTC/USD or BTC/EUR rates missing in API response");
            }

            // Create ConversionReport entity
            ConversionReport report = new ConversionReport();
            report.setJobTechnicalId(job.getId());
            report.setCreatedTimestamp(LocalDateTime.now());
            report.setBtcUsdRate(BigDecimal.valueOf(btcUsd));
            report.setBtcEurRate(BigDecimal.valueOf(btcEur));
            report.setStatus("CREATED");
            report.setEmailSentTimestamp(null);

            conversionReportCache.put(job.getId(), report);
            log.info("Created ConversionReport for Job ID: {}", job.getId());

            job.setStatus("FETCHING_COMPLETED");
            reportJobCache.put(job.getId(), job);

            // Send email (simulate email sending)
            boolean emailSent = sendEmailReport(job.getRecipientEmail(), report);

            if (emailSent) {
                report.setStatus("EMAILED");
                report.setEmailSentTimestamp(LocalDateTime.now());
                conversionReportCache.put(job.getId(), report);

                job.setStatus("COMPLETED");
                job.setErrorMessage(null);
                reportJobCache.put(job.getId(), job);

                log.info("Email sent successfully for ReportJob ID: {}", job.getId());
            } else {
                job.setStatus("FAILED");
                job.setErrorMessage("Failed to send email");
                reportJobCache.put(job.getId(), job);
                log.error("Failed to send email for ReportJob ID: {}", job.getId());
            }

        } catch (Exception e) {
            log.error("Error processing ReportJob ID {}: {}", job.getId(), e.getMessage());
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage());
            reportJobCache.put(job.getId(), job);
        }
    }

    private boolean sendEmailReport(String recipientEmail, ConversionReport report) {
        // Simulate email sending logic here
        // In real implementation, use JavaMailSender or external email service API
        log.info("Sending email report to {} with BTC/USD: {}, BTC/EUR: {}", recipientEmail, report.getBtcUsdRate(), report.getBtcEurRate());
        return true; // Simulate success
    }

    // Entity Classes for prototype cache

    public static class ReportJob {
        private String id;
        private LocalDateTime requestTimestamp;
        private String status;
        private String recipientEmail;
        private String errorMessage;

        public ReportJob() {}

        public String getId() {
            return id;
        }
        public void setId(String id) {
            this.id = id;
        }
        public LocalDateTime getRequestTimestamp() {
            return requestTimestamp;
        }
        public void setRequestTimestamp(LocalDateTime requestTimestamp) {
            this.requestTimestamp = requestTimestamp;
        }
        public String getStatus() {
            return status;
        }
        public void setStatus(String status) {
            this.status = status;
        }
        public String getRecipientEmail() {
            return recipientEmail;
        }
        public void setRecipientEmail(String recipientEmail) {
            this.recipientEmail = recipientEmail;
        }
        public String getErrorMessage() {
            return errorMessage;
        }
        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }
    }

    public static class ConversionReport {
        private String jobTechnicalId;
        private LocalDateTime createdTimestamp;
        private BigDecimal btcUsdRate;
        private BigDecimal btcEurRate;
        private LocalDateTime emailSentTimestamp;
        private String status;

        public ConversionReport() {}

        public String getJobTechnicalId() {
            return jobTechnicalId;
        }
        public void setJobTechnicalId(String jobTechnicalId) {
            this.jobTechnicalId = jobTechnicalId;
        }
        public LocalDateTime getCreatedTimestamp() {
            return createdTimestamp;
        }
        public void setCreatedTimestamp(LocalDateTime createdTimestamp) {
            this.createdTimestamp = createdTimestamp;
        }
        public BigDecimal getBtcUsdRate() {
            return btcUsdRate;
        }
        public void setBtcUsdRate(BigDecimal btcUsdRate) {
            this.btcUsdRate = btcUsdRate;
        }
        public BigDecimal getBtcEurRate() {
            return btcEurRate;
        }
        public void setBtcEurRate(BigDecimal btcEurRate) {
            this.btcEurRate = btcEurRate;
        }
        public LocalDateTime getEmailSentTimestamp() {
            return emailSentTimestamp;
        }
        public void setEmailSentTimestamp(LocalDateTime emailSentTimestamp) {
            this.emailSentTimestamp = emailSentTimestamp;
        }
        public String getStatus() {
            return status;
        }
        public void setStatus(String status) {
            this.status = status;
        }
    }
}