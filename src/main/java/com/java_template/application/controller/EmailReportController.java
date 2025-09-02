package com.java_template.application.controller;

import com.java_template.application.entity.emailreport.version_1.EmailReport;
import com.java_template.common.dto.EntityResponse;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/email-reports")
public class EmailReportController {

    private static final Logger logger = LoggerFactory.getLogger(EmailReportController.class);
    private final EntityService entityService;

    public EmailReportController(EntityService entityService) {
        this.entityService = entityService;
    }

    @GetMapping("/{reportId}")
    public ResponseEntity<?> getEmailReport(@PathVariable String reportId) {
        try {
            logger.info("Getting EmailReport by reportId: {}", reportId);
            
            // Find by business ID (reportId)
            EntityResponse<EmailReport> response = entityService.findByBusinessId(
                EmailReport.class,
                EmailReport.ENTITY_NAME,
                EmailReport.ENTITY_VERSION,
                reportId,
                "reportId"
            );
            
            EmailReportDto responseDto = createResponseDto(response);
            return ResponseEntity.ok(responseDto);
            
        } catch (Exception e) {
            logger.error("Failed to get EmailReport by reportId: {}", reportId, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "ENTITY_NOT_FOUND", 
                           "message", "EmailReport with ID " + reportId + " not found",
                           "timestamp", java.time.LocalDateTime.now()));
        }
    }

    @GetMapping("/by-request/{requestId}")
    public ResponseEntity<?> getEmailReportByRequest(@PathVariable String requestId) {
        try {
            logger.info("Getting EmailReport by requestId: {}", requestId);
            
            Condition requestIdCondition = Condition.of("$.requestId", "EQUALS", requestId);
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("group");
            condition.setOperator("AND");
            condition.setConditions(List.of(requestIdCondition));
            
            Optional<EntityResponse<EmailReport>> reportResponse = 
                entityService.getFirstItemByCondition(
                    EmailReport.class,
                    EmailReport.ENTITY_NAME,
                    EmailReport.ENTITY_VERSION,
                    condition,
                    true
                );
            
            if (!reportResponse.isPresent()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "ENTITY_NOT_FOUND", 
                               "message", "EmailReport for request " + requestId + " not found",
                               "timestamp", java.time.LocalDateTime.now()));
            }
            
            EmailReportDto responseDto = createResponseDto(reportResponse.get());
            return ResponseEntity.ok(responseDto);
            
        } catch (Exception e) {
            logger.error("Failed to get EmailReport by requestId: {}", requestId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "RETRIEVAL_FAILED", 
                           "message", "Failed to retrieve EmailReport for request: " + e.getMessage(),
                           "timestamp", java.time.LocalDateTime.now()));
        }
    }

    @GetMapping
    public ResponseEntity<?> getAllEmailReports() {
        try {
            logger.info("Getting all EmailReports");
            
            List<EntityResponse<EmailReport>> responses = entityService.findAll(
                EmailReport.class,
                EmailReport.ENTITY_NAME,
                EmailReport.ENTITY_VERSION
            );
            
            List<EmailReportDto> responseDtos = responses.stream()
                .map(this::createResponseDto)
                .toList();
                
            return ResponseEntity.ok(responseDtos);
            
        } catch (Exception e) {
            logger.error("Failed to get all EmailReports", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "RETRIEVAL_FAILED", 
                           "message", "Failed to retrieve EmailReports: " + e.getMessage(),
                           "timestamp", java.time.LocalDateTime.now()));
        }
    }

    @PostMapping("/{reportId}/resend")
    public ResponseEntity<?> resendEmailReport(@PathVariable String reportId) {
        try {
            logger.info("Resending EmailReport with reportId: {}", reportId);
            
            // Get existing entity
            EntityResponse<EmailReport> existingResponse = entityService.findByBusinessId(
                EmailReport.class,
                EmailReport.ENTITY_NAME,
                EmailReport.ENTITY_VERSION,
                reportId,
                "reportId"
            );
            
            EmailReport entity = existingResponse.getData();
            UUID entityId = existingResponse.getMetadata().getId();
            
            // Reset failure fields and status
            entity.setFailedAt(null);
            entity.setFailureReason(null);
            entity.setEmailStatus("SENDING");
            entity.setSentAt(null);
            
            // Update with "send_email" transition to resend
            EntityResponse<EmailReport> response = entityService.update(
                entityId, entity, "send_email"
            );
            
            EmailReportDto responseDto = createResponseDto(response);
            return ResponseEntity.ok(responseDto);
            
        } catch (Exception e) {
            logger.error("Failed to resend EmailReport with reportId: {}", reportId, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "RESEND_FAILED", 
                           "message", "Failed to resend EmailReport: " + e.getMessage(),
                           "timestamp", java.time.LocalDateTime.now()));
        }
    }

    private EmailReportDto createResponseDto(EntityResponse<EmailReport> response) {
        EmailReport entity = response.getData();
        String state = response.getMetadata().getState();
        
        EmailReportDto dto = new EmailReportDto();
        dto.setReportId(entity.getReportId());
        dto.setRequestId(entity.getRequestId());
        dto.setRecipientEmail(entity.getRecipientEmail());
        dto.setSubject(entity.getSubject());
        dto.setEmailStatus(entity.getEmailStatus());
        dto.setSentAt(entity.getSentAt());
        dto.setState(state);
        
        return dto;
    }

    // DTO
    public static class EmailReportDto {
        private String reportId;
        private String requestId;
        private String recipientEmail;
        private String subject;
        private String emailStatus;
        private java.time.LocalDateTime sentAt;
        private String state;

        // Getters and setters
        public String getReportId() { return reportId; }
        public void setReportId(String reportId) { this.reportId = reportId; }
        public String getRequestId() { return requestId; }
        public void setRequestId(String requestId) { this.requestId = requestId; }
        public String getRecipientEmail() { return recipientEmail; }
        public void setRecipientEmail(String recipientEmail) { this.recipientEmail = recipientEmail; }
        public String getSubject() { return subject; }
        public void setSubject(String subject) { this.subject = subject; }
        public String getEmailStatus() { return emailStatus; }
        public void setEmailStatus(String emailStatus) { this.emailStatus = emailStatus; }
        public java.time.LocalDateTime getSentAt() { return sentAt; }
        public void setSentAt(java.time.LocalDateTime sentAt) { this.sentAt = sentAt; }
        public String getState() { return state; }
        public void setState(String state) { this.state = state; }
    }
}
