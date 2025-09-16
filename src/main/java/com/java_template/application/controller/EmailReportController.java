package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.email_report.version_1.EmailReport;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * EmailReportController - Manage email report generation and delivery
 * 
 * Base Path: /api/email-reports
 * Purpose: Manage email report generation and delivery
 */
@RestController
@RequestMapping("/api/email-reports")
@CrossOrigin(origins = "*")
public class EmailReportController {

    private static final Logger logger = LoggerFactory.getLogger(EmailReportController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public EmailReportController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Get email report by technical UUID
     * GET /api/email-reports/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<EmailReport>> getEmailReportById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(EmailReport.ENTITY_NAME).withVersion(EmailReport.ENTITY_VERSION);
            EntityWithMetadata<EmailReport> response = entityService.getById(id, modelSpec, EmailReport.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting EmailReport by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Send prepared email report
     * PUT /api/email-reports/{id}/send
     */
    @PutMapping("/{id}/send")
    public ResponseEntity<EntityWithMetadata<EmailReport>> sendEmailReport(
            @PathVariable UUID id,
            @RequestParam(required = false) String transition) {
        try {
            // Get current email report
            ModelSpec modelSpec = new ModelSpec().withName(EmailReport.ENTITY_NAME).withVersion(EmailReport.ENTITY_VERSION);
            EntityWithMetadata<EmailReport> currentReport = entityService.getById(id, modelSpec, EmailReport.class);

            // Use provided transition or default to send_email
            String transitionName = (transition != null) ? transition : "send_email";

            // Update with transition to trigger email sending
            EntityWithMetadata<EmailReport> response = entityService.update(id, currentReport.entity(), transitionName);
            logger.info("Email sending initiated for report ID: {}", id);
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error sending email report for ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Retry failed email delivery
     * PUT /api/email-reports/{id}/retry
     */
    @PutMapping("/{id}/retry")
    public ResponseEntity<EntityWithMetadata<EmailReport>> retryEmailReport(
            @PathVariable UUID id,
            @RequestParam(required = false) String transition) {
        try {
            // Get current email report
            ModelSpec modelSpec = new ModelSpec().withName(EmailReport.ENTITY_NAME).withVersion(EmailReport.ENTITY_VERSION);
            EntityWithMetadata<EmailReport> currentReport = entityService.getById(id, modelSpec, EmailReport.class);

            // Use provided transition or default to retry_email
            String transitionName = (transition != null) ? transition : "retry_email";

            // Update with transition to trigger retry
            EntityWithMetadata<EmailReport> response = entityService.update(id, currentReport.entity(), transitionName);
            logger.info("Email retry initiated for report ID: {}", id);
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error retrying email report for ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get email report by analysis ID
     * GET /api/email-reports/analysis/{analysisId}
     */
    @GetMapping("/analysis/{analysisId}")
    public ResponseEntity<EntityWithMetadata<EmailReport>> getEmailReportByAnalysisId(@PathVariable String analysisId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(EmailReport.ENTITY_NAME).withVersion(EmailReport.ENTITY_VERSION);

            SimpleCondition analysisIdCondition = new SimpleCondition()
                    .withJsonPath("$.analysisId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(analysisId));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(analysisIdCondition));

            List<EntityWithMetadata<EmailReport>> reports = entityService.search(modelSpec, condition, EmailReport.class);
            
            if (reports.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            return ResponseEntity.ok(reports.get(0));
        } catch (Exception e) {
            logger.error("Error getting EmailReport by analysisId: {}", analysisId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get all email reports (use sparingly)
     * GET /api/email-reports
     */
    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<EmailReport>>> getAllEmailReports() {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(EmailReport.ENTITY_NAME).withVersion(EmailReport.ENTITY_VERSION);
            List<EntityWithMetadata<EmailReport>> reports = entityService.findAll(modelSpec, EmailReport.class);
            return ResponseEntity.ok(reports);
        } catch (Exception e) {
            logger.error("Error getting all EmailReports", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update email report with optional transition
     * PUT /api/email-reports/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<EmailReport>> updateEmailReport(
            @PathVariable UUID id,
            @RequestBody EmailReport emailReport,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<EmailReport> response = entityService.update(id, emailReport, transition);
            logger.info("EmailReport updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating EmailReport", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete email report by technical UUID
     * DELETE /api/email-reports/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEmailReport(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("EmailReport deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting EmailReport", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get email reports by delivery status
     * GET /api/email-reports/status/{status}
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<List<EntityWithMetadata<EmailReport>>> getEmailReportsByStatus(@PathVariable String status) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(EmailReport.ENTITY_NAME).withVersion(EmailReport.ENTITY_VERSION);

            SimpleCondition statusCondition = new SimpleCondition()
                    .withJsonPath("$.deliveryStatus")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(status));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(statusCondition));

            List<EntityWithMetadata<EmailReport>> reports = entityService.search(modelSpec, condition, EmailReport.class);
            return ResponseEntity.ok(reports);
        } catch (Exception e) {
            logger.error("Error getting EmailReports by status: {}", status, e);
            return ResponseEntity.badRequest().build();
        }
    }
}
