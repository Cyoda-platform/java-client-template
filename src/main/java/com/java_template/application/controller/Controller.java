package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.DataDownload;
import com.java_template.application.entity.ReportEmail;
import com.java_template.application.entity.Workflow;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.UUID;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/controller")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private final EntityService entityService;

    // POST /controller/workflow - create Workflow entity
    @PostMapping("/workflow")
    public ResponseEntity<Map<String, String>> createWorkflow(@RequestBody Workflow workflow) {
        try {
            if (workflow == null || !workflow.isValid()) {
                log.error("Invalid Workflow entity received");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Workflow.ENTITY_NAME,
                    ENTITY_VERSION,
                    workflow
            );
            UUID technicalId = idFuture.get();
            log.info("Workflow created with technicalId: {}", technicalId);

            processWorkflow(technicalId, workflow);

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId.toString());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            log.error("Illegal argument exception in createWorkflow: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            log.error("Error in createWorkflow: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // GET /controller/workflow/{id} - get Workflow by technicalId
    @GetMapping("/workflow/{id}")
    public ResponseEntity<Workflow> getWorkflow(@PathVariable("id") UUID technicalId) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Workflow.ENTITY_NAME,
                    ENTITY_VERSION,
                    technicalId
            );
            ObjectNode node = itemFuture.get();
            if (node == null) {
                log.error("Workflow not found for technicalId: {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Workflow workflow = node.traverse().readValueAs(Workflow.class);
            return ResponseEntity.ok(workflow);
        } catch (IllegalArgumentException e) {
            log.error("Illegal argument exception in getWorkflow: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            log.error("Error in getWorkflow: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // GET /controller/datadownload/{id} - get DataDownload by technicalId
    @GetMapping("/datadownload/{id}")
    public ResponseEntity<DataDownload> getDataDownload(@PathVariable("id") UUID technicalId) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    DataDownload.ENTITY_NAME,
                    ENTITY_VERSION,
                    technicalId
            );
            ObjectNode node = itemFuture.get();
            if (node == null) {
                log.error("DataDownload not found for technicalId: {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            DataDownload dataDownload = node.traverse().readValueAs(DataDownload.class);
            return ResponseEntity.ok(dataDownload);
        } catch (IllegalArgumentException e) {
            log.error("Illegal argument exception in getDataDownload: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            log.error("Error in getDataDownload: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // GET /controller/reportemail/{id} - get ReportEmail by technicalId
    @GetMapping("/reportemail/{id}")
    public ResponseEntity<ReportEmail> getReportEmail(@PathVariable("id") UUID technicalId) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    ReportEmail.ENTITY_NAME,
                    ENTITY_VERSION,
                    technicalId
            );
            ObjectNode node = itemFuture.get();
            if (node == null) {
                log.error("ReportEmail not found for technicalId: {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            ReportEmail reportEmail = node.traverse().readValueAs(ReportEmail.class);
            return ResponseEntity.ok(reportEmail);
        } catch (IllegalArgumentException e) {
            log.error("Illegal argument exception in getReportEmail: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            log.error("Error in getReportEmail: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Business logic: process Workflow entity after creation
    private void processWorkflow(UUID technicalId, Workflow workflow) {
        log.info("Processing Workflow with technicalId: {}", technicalId);
        try {
            workflow.setStatus("PROCESSING");
            // TODO: update workflow status, currently no update method available
            // Could be implemented if update method is added in EntityService

            if (workflow.getUrl() == null || workflow.getUrl().isBlank()) {
                workflow.setStatus("FAILED");
                // TODO: update workflow status
                log.error("Workflow validation failed: URL is blank");
                return;
            }
            if (workflow.getSubscribers() == null || workflow.getSubscribers().isEmpty()) {
                workflow.setStatus("FAILED");
                // TODO: update workflow status
                log.error("Workflow validation failed: Subscribers list is empty");
                return;
            }

            // Create DataDownload entity to start data fetch
            DataDownload dataDownload = new DataDownload();
            dataDownload.setWorkflowTechnicalId(technicalId.toString());
            dataDownload.setDownloadUrl(workflow.getUrl());
            dataDownload.setStatus("PENDING");

            CompletableFuture<UUID> dataDownloadIdFuture = entityService.addItem(
                    DataDownload.ENTITY_NAME,
                    ENTITY_VERSION,
                    dataDownload
            );
            UUID dataDownloadId = dataDownloadIdFuture.get();
            log.info("DataDownload created with technicalId: {}", dataDownloadId);

            // Trigger processing of DataDownload
            processDataDownload(dataDownloadId, dataDownload);

        } catch (Exception e) {
            log.error("Error processing Workflow: {}", e.getMessage());
        }
    }

    // Business logic: process DataDownload entity after creation
    private void processDataDownload(UUID technicalId, DataDownload dataDownload) {
        log.info("Processing DataDownload with technicalId: {}", technicalId);
        try {
            dataDownload.setStatus("PROCESSING");
            // TODO: update dataDownload status

            // Simulate download content
            String csvData = "id,address,price\n1,123 A St,100000\n2,456 B Ave,150000\n";

            dataDownload.setDataContent(csvData);
            dataDownload.setStatus("SUCCESS");
            dataDownload.setTimestamp(java.time.Instant.now().toString());
            // TODO: update dataDownload entity

            // Analyze data and generate report string (simulate simple analysis)
            String report = "Report Summary: 2 entries, average price = 125000";

            // Update Workflow with report
            UUID workflowTechnicalId = UUID.fromString(dataDownload.getWorkflowTechnicalId());
            CompletableFuture<ObjectNode> workflowNodeFuture = entityService.getItem(
                    Workflow.ENTITY_NAME,
                    ENTITY_VERSION,
                    workflowTechnicalId
            );
            ObjectNode workflowNode = workflowNodeFuture.get();
            if (workflowNode != null) {
                Workflow workflow = workflowNode.traverse().readValueAs(Workflow.class);
                if (workflow != null) {
                    workflow.setReport(report);
                    workflow.setStatus("COMPLETED");
                    // TODO: update workflow entity
                    List<String> subscribers = workflow.getSubscribers() != null ? workflow.getSubscribers() : Collections.emptyList();
                    for (String email : subscribers) {
                        ReportEmail reportEmail = new ReportEmail();
                        reportEmail.setWorkflowTechnicalId(dataDownload.getWorkflowTechnicalId());
                        reportEmail.setEmailTo(email);
                        reportEmail.setEmailContent(report);
                        reportEmail.setStatus("PENDING");
                        reportEmail.setTimestamp(null);

                        CompletableFuture<UUID> reportEmailIdFuture = entityService.addItem(
                                ReportEmail.ENTITY_NAME,
                                ENTITY_VERSION,
                                reportEmail
                        );
                        UUID reportEmailId = reportEmailIdFuture.get();
                        log.info("ReportEmail created for subscriber: {} with technicalId: {}", email, reportEmailId);

                        // Trigger sending email
                        processReportEmail(reportEmailId, reportEmail);
                    }
                }
            }

        } catch (Exception e) {
            dataDownload.setStatus("FAILED");
            // TODO: update dataDownload entity
            log.error("DataDownload processing failed: {}", e.getMessage());

            try {
                UUID workflowTechnicalId = UUID.fromString(dataDownload.getWorkflowTechnicalId());
                CompletableFuture<ObjectNode> workflowNodeFuture = entityService.getItem(
                        Workflow.ENTITY_NAME,
                        ENTITY_VERSION,
                        workflowTechnicalId
                );
                ObjectNode workflowNode = workflowNodeFuture.get();
                if (workflowNode != null) {
                    Workflow workflow = workflowNode.traverse().readValueAs(Workflow.class);
                    if (workflow != null) {
                        workflow.setStatus("FAILED");
                        // TODO: update workflow entity
                    }
                }
            } catch (Exception ex) {
                log.error("Error updating Workflow status to FAILED: {}", ex.getMessage());
            }
        }
    }

    // Business logic: process ReportEmail entity after creation
    private void processReportEmail(UUID technicalId, ReportEmail reportEmail) {
        log.info("Processing ReportEmail with technicalId: {}", technicalId);
        try {
            reportEmail.setStatus("SENDING");
            // TODO: update reportEmail entity

            // Simulate sending email (in real app use JavaMailSender)
            log.info("Sending email to: {}", reportEmail.getEmailTo());
            // Simulate successful send
            reportEmail.setStatus("SENT");
            reportEmail.setTimestamp(java.time.Instant.now().toString());
            // TODO: update reportEmail entity
            log.info("Email sent successfully to: {}", reportEmail.getEmailTo());
        } catch (Exception e) {
            reportEmail.setStatus("FAILED");
            // TODO: update reportEmail entity
            log.error("Failed to send email to {}: {}", reportEmail.getEmailTo(), e.getMessage());
        }
    }
}