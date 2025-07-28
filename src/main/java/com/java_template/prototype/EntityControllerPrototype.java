package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import com.java_template.application.entity.ReportJob;
import com.java_template.application.entity.EmailReport;
import com.java_template.application.entity.Report;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, ReportJob> reportJobCache = new ConcurrentHashMap<>();
    private final AtomicLong reportJobIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, EmailReport> emailReportCache = new ConcurrentHashMap<>();
    private final AtomicLong emailReportIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, Report> reportCache = new ConcurrentHashMap<>();
    private final AtomicLong reportIdCounter = new AtomicLong(1);

    // POST /prototype/reportJob - create ReportJob entity
    @PostMapping("/reportJob")
    public ResponseEntity<Map<String, String>> createReportJob(@RequestBody(required = false) Map<String, Object> requestBody) {
        try {
            ReportJob reportJob = new ReportJob();

            // Initialize with default or empty values as request body is empty/optional for prototype
            reportJob.setBtcUsdRate(BigDecimal.ZERO);
            reportJob.setBtcEurRate(BigDecimal.ZERO);
            reportJob.setTimestamp(OffsetDateTime.now());
            reportJob.setEmailStatus("PENDING");

            String technicalId = Long.toString(reportJobIdCounter.getAndIncrement());
            reportJobCache.put(technicalId, reportJob);

            processReportJob(technicalId, reportJob);

            log.info("Created ReportJob with id {}", technicalId);
            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (Exception e) {
            log.error("Failed to create ReportJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // GET /prototype/reportJob/{id} - retrieve ReportJob by technicalId
    @GetMapping("/reportJob/{id}")
    public ResponseEntity<ReportJob> getReportJob(@PathVariable("id") String id) {
        ReportJob reportJob = reportJobCache.get(id);
        if (reportJob == null) {
            log.error("ReportJob not found with id {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(reportJob);
    }

    // GET /prototype/report/{id} - retrieve Report by technicalId
    @GetMapping("/report/{id}")
    public ResponseEntity<Report> getReport(@PathVariable("id") String id) {
        Report report = reportCache.get(id);
        if (report == null) {
            log.error("Report not found with id {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(report);
    }

    // No POST for EmailReport creation as it's triggered internally by processing ReportJob

    // Process ReportJob business logic
    private void processReportJob(String technicalId, ReportJob reportJob) {
        try {
            // Simulate fetching BTC/USD and BTC/EUR rates from external API
            BigDecimal fetchedBtcUsdRate = fetchBtcUsdRate();
            BigDecimal fetchedBtcEurRate = fetchBtcEurRate();
            OffsetDateTime now = OffsetDateTime.now();

            reportJob.setBtcUsdRate(fetchedBtcUsdRate);
            reportJob.setBtcEurRate(fetchedBtcEurRate);
            reportJob.setTimestamp(now);

            // Update cache with fetched data
            reportJobCache.put(technicalId, reportJob);

            // Create immutable Report entity
            Report report = new Report();
            report.setReportJobId(technicalId);
            report.setBtcUsdRate(fetchedBtcUsdRate);
            report.setBtcEurRate(fetchedBtcEurRate);
            report.setTimestamp(now);

            String reportId = Long.toString(reportIdCounter.getAndIncrement());
            reportCache.put(reportId, report);

            // Create EmailReport entity to send email
            EmailReport emailReport = new EmailReport();
            emailReport.setReportJobId(technicalId);
            emailReport.setRecipient("recipient@example.com"); // hardcoded recipient for prototype
            emailReport.setSubject("Bitcoin Conversion Rate Report");
            emailReport.setBody(String.format(
                    "BTC/USD: %s\nBTC/EUR: %s\nTimestamp: %s",
                    fetchedBtcUsdRate.toPlainString(),
                    fetchedBtcEurRate.toPlainString(),
                    now.toString()
            ));
            emailReport.setStatus("PENDING");
            emailReport.setSentTimestamp(null);

            String emailReportId = Long.toString(emailReportIdCounter.getAndIncrement());
            emailReportCache.put(emailReportId, emailReport);

            // Process EmailReport to send email
            processEmailReport(emailReportId, emailReport, reportJob, technicalId);

        } catch (Exception e) {
            log.error("Error processing ReportJob with id {}", technicalId, e);
            reportJob.setEmailStatus("FAILED");
            reportJobCache.put(technicalId, reportJob);
        }
    }

    // Simulate external API call to fetch BTC/USD rate
    private BigDecimal fetchBtcUsdRate() {
        // For prototype, return fixed dummy value
        return new BigDecimal("30123.45");
    }

    // Simulate external API call to fetch BTC/EUR rate
    private BigDecimal fetchBtcEurRate() {
        // For prototype, return fixed dummy value
        return new BigDecimal("27950.30");
    }

    // Process EmailReport entity to send email
    private void processEmailReport(String emailReportId, EmailReport emailReport, ReportJob reportJob, String reportJobId) {
        try {
            // Simulate sending email
            log.info("Sending email to {}", emailReport.getRecipient());
            // Here would be real email sending logic (SMTP, API, etc.)

            // Mark email as SENT
            emailReport.setStatus("SENT");
            emailReport.setSentTimestamp(OffsetDateTime.now());
            emailReportCache.put(emailReportId, emailReport);

            // Update ReportJob emailStatus
            reportJob.setEmailStatus("SENT");
            reportJobCache.put(reportJobId, reportJob);

            log.info("Email sent successfully to {}", emailReport.getRecipient());

        } catch (Exception e) {
            log.error("Failed to send email for EmailReport id {}", emailReportId, e);
            emailReport.setStatus("FAILED");
            emailReportCache.put(emailReportId, emailReport);

            reportJob.setEmailStatus("FAILED");
            reportJobCache.put(reportJobId, reportJob);
        }
    }
}