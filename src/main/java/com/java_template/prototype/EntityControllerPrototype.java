package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import com.java_template.application.entity.Workflow;
import com.java_template.application.entity.DataDownload;
import com.java_template.application.entity.ReportEmail;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, Workflow> workflowCache = new ConcurrentHashMap<>();
    private final AtomicLong workflowIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, DataDownload> dataDownloadCache = new ConcurrentHashMap<>();
    private final AtomicLong dataDownloadIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, ReportEmail> reportEmailCache = new ConcurrentHashMap<>();
    private final AtomicLong reportEmailIdCounter = new AtomicLong(1);

    // POST /prototype/workflow - create Workflow entity
    @PostMapping("/workflow")
    public ResponseEntity<Map<String, String>> createWorkflow(@RequestBody Workflow workflow) {
        if (workflow == null || !workflow.isValid()) {
            log.error("Invalid Workflow entity received");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        String technicalId = "wf-" + workflowIdCounter.getAndIncrement();
        workflowCache.put(technicalId, workflow);
        log.info("Workflow created with technicalId: {}", technicalId);

        try {
            processWorkflow(technicalId, workflow);
        } catch (Exception e) {
            log.error("Error processing Workflow: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        Map<String, String> response = new HashMap<>();
        response.put("technicalId", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // GET /prototype/workflow/{id} - get Workflow by technicalId
    @GetMapping("/workflow/{id}")
    public ResponseEntity<Workflow> getWorkflow(@PathVariable("id") String technicalId) {
        Workflow workflow = workflowCache.get(technicalId);
        if (workflow == null) {
            log.error("Workflow not found for technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(workflow);
    }

    // GET /prototype/datadownload/{id} - get DataDownload by technicalId
    @GetMapping("/datadownload/{id}")
    public ResponseEntity<DataDownload> getDataDownload(@PathVariable("id") String technicalId) {
        DataDownload dataDownload = dataDownloadCache.get(technicalId);
        if (dataDownload == null) {
            log.error("DataDownload not found for technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(dataDownload);
    }

    // GET /prototype/reportemail/{id} - get ReportEmail by technicalId
    @GetMapping("/reportemail/{id}")
    public ResponseEntity<ReportEmail> getReportEmail(@PathVariable("id") String technicalId) {
        ReportEmail reportEmail = reportEmailCache.get(technicalId);
        if (reportEmail == null) {
            log.error("ReportEmail not found for technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(reportEmail);
    }

    // Business logic: process Workflow entity after creation
    private void processWorkflow(String technicalId, Workflow workflow) {
        log.info("Processing Workflow with technicalId: {}", technicalId);
        workflow.setStatus("PROCESSING");
        workflowCache.put(technicalId, workflow);

        // Validate URL and subscribers 
        if (workflow.getUrl() == null || workflow.getUrl().isBlank()) {
            workflow.setStatus("FAILED");
            workflowCache.put(technicalId, workflow);
            log.error("Workflow validation failed: URL is blank");
            return;
        }
        if (workflow.getSubscribers() == null || workflow.getSubscribers().isEmpty()) {
            workflow.setStatus("FAILED");
            workflowCache.put(technicalId, workflow);
            log.error("Workflow validation failed: Subscribers list is empty");
            return;
        }

        // Create DataDownload entity to start data fetch
        DataDownload dataDownload = new DataDownload();
        dataDownload.setWorkflowTechnicalId(technicalId);
        dataDownload.setDownloadUrl(workflow.getUrl());
        dataDownload.setStatus("PENDING");
        String dataDownloadId = "dd-" + dataDownloadIdCounter.getAndIncrement();
        dataDownloadCache.put(dataDownloadId, dataDownload);
        log.info("DataDownload created with technicalId: {}", dataDownloadId);

        // Trigger processing of DataDownload
        processDataDownload(dataDownloadId, dataDownload);
    }

    // Business logic: process DataDownload entity after creation
    private void processDataDownload(String technicalId, DataDownload dataDownload) {
        log.info("Processing DataDownload with technicalId: {}", technicalId);
        dataDownload.setStatus("PROCESSING");
        dataDownloadCache.put(technicalId, dataDownload);

        try {
            // Download CSV data from downloadUrl using Spring WebClient or RestTemplate
            // For prototype, simulate download content
            String csvData = "id,address,price\n1,123 A St,100000\n2,456 B Ave,150000\n"; // Simulated CSV content

            dataDownload.setDataContent(csvData);
            dataDownload.setStatus("SUCCESS");
            dataDownload.setTimestamp(java.time.Instant.now().toString());
            dataDownloadCache.put(technicalId, dataDownload);
            log.info("DataDownload successful for technicalId: {}", technicalId);

            // Analyze data and generate report string (simulate simple analysis)
            String report = "Report Summary: 2 entries, average price = 125000";

            // Update Workflow with report
            Workflow workflow = workflowCache.get(dataDownload.getWorkflowTechnicalId());
            if (workflow != null) {
                workflow.setReport(report);
                workflow.setStatus("COMPLETED");
                workflowCache.put(dataDownload.getWorkflowTechnicalId(), workflow);
            }

            // For each subscriber, create ReportEmail entity
            List<String> subscribers = workflow != null ? workflow.getSubscribers() : Collections.emptyList();
            for (String email : subscribers) {
                ReportEmail reportEmail = new ReportEmail();
                reportEmail.setWorkflowTechnicalId(dataDownload.getWorkflowTechnicalId());
                reportEmail.setEmailTo(email);
                reportEmail.setEmailContent(report);
                reportEmail.setStatus("PENDING");
                reportEmail.setTimestamp(null);
                String reportEmailId = "re-" + reportEmailIdCounter.getAndIncrement();
                reportEmailCache.put(reportEmailId, reportEmail);
                log.info("ReportEmail created for subscriber: {} with technicalId: {}", email, reportEmailId);

                // Trigger sending email
                processReportEmail(reportEmailId, reportEmail);
            }

        } catch (Exception e) {
            dataDownload.setStatus("FAILED");
            dataDownloadCache.put(technicalId, dataDownload);
            log.error("DataDownload processing failed: {}", e.getMessage());

            // Update Workflow status to FAILED
            Workflow workflow = workflowCache.get(dataDownload.getWorkflowTechnicalId());
            if (workflow != null) {
                workflow.setStatus("FAILED");
                workflowCache.put(dataDownload.getWorkflowTechnicalId(), workflow);
            }
        }
    }

    // Business logic: process ReportEmail entity after creation
    private void processReportEmail(String technicalId, ReportEmail reportEmail) {
        log.info("Processing ReportEmail with technicalId: {}", technicalId);
        reportEmail.setStatus("SENDING");
        reportEmailCache.put(technicalId, reportEmail);

        try {
            // Simulate sending email (in real app use JavaMailSender)
            log.info("Sending email to: {}", reportEmail.getEmailTo());
            // Simulate successful send
            reportEmail.setStatus("SENT");
            reportEmail.setTimestamp(java.time.Instant.now().toString());
            reportEmailCache.put(technicalId, reportEmail);
            log.info("Email sent successfully to: {}", reportEmail.getEmailTo());
        } catch (Exception e) {
            reportEmail.setStatus("FAILED");
            reportEmailCache.put(technicalId, reportEmail);
            log.error("Failed to send email to {}: {}", reportEmail.getEmailTo(), e.getMessage());
        }
    }
}