package com.java_template.application.controller;

import com.java_template.application.entity.emailnotification.version_1.EmailNotification;
import com.java_template.common.dto.EntityResponse;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.util.Condition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/email-notifications")
public class EmailNotificationController {

    private static final Logger logger = LoggerFactory.getLogger(EmailNotificationController.class);

    @Autowired
    private EntityService entityService;

    @PostMapping
    public ResponseEntity<EntityResponse<EmailNotification>> createEmailNotification(@RequestBody EmailNotification email) {
        try {
            logger.info("Creating new email notification for: {}", email.getRecipientEmail());
            EntityResponse<EmailNotification> response = entityService.save(email);
            logger.info("Email notification created with ID: {}", response.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            logger.error("Failed to create email notification: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntityResponse<EmailNotification>> getEmailNotification(@PathVariable UUID id) {
        try {
            logger.info("Retrieving email notification with ID: {}", id);
            EntityResponse<EmailNotification> response = entityService.getItem(id, EmailNotification.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to retrieve email notification {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping
    public ResponseEntity<List<EntityResponse<EmailNotification>>> getAllEmailNotifications() {
        try {
            logger.info("Retrieving all email notifications");
            List<EntityResponse<EmailNotification>> emails = entityService.getItems(
                EmailNotification.class,
                EmailNotification.ENTITY_NAME,
                EmailNotification.ENTITY_VERSION,
                null,
                null,
                null
            );
            return ResponseEntity.ok(emails);
        } catch (Exception e) {
            logger.error("Failed to retrieve email notifications: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/report/{reportId}")
    public ResponseEntity<List<EntityResponse<EmailNotification>>> getEmailsByReport(@PathVariable Long reportId) {
        try {
            logger.info("Retrieving email notifications for report: {}", reportId);
            
            Condition reportIdCondition = Condition.of("$.reportId", "EQUALS", reportId.toString());
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("group");
            condition.setOperator("AND");
            condition.setConditions(List.of(reportIdCondition));
            
            List<EntityResponse<EmailNotification>> emails = entityService.getItemsByCondition(
                EmailNotification.class,
                EmailNotification.ENTITY_NAME,
                EmailNotification.ENTITY_VERSION,
                condition,
                true
            );
            
            return ResponseEntity.ok(emails);
        } catch (Exception e) {
            logger.error("Failed to retrieve email notifications for report {}: {}", reportId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/search")
    public ResponseEntity<List<EntityResponse<EmailNotification>>> searchEmailNotifications(
            @RequestParam(required = false) String recipientEmail,
            @RequestParam(required = false) String deliveryStatus,
            @RequestParam(required = false) Long reportId,
            @RequestParam(required = false) String state) {
        try {
            logger.info("Searching email notifications with filters - recipient: {}, status: {}, reportId: {}, state: {}", 
                       recipientEmail, deliveryStatus, reportId, state);
            
            SearchConditionRequest condition = new SearchConditionRequest();
            condition.setType("group");
            condition.setOperator("AND");
            
            List<Condition> conditions = new java.util.ArrayList<>();
            
            if (recipientEmail != null && !recipientEmail.trim().isEmpty()) {
                conditions.add(Condition.of("$.recipientEmail", "EQUALS", recipientEmail));
            }
            if (deliveryStatus != null && !deliveryStatus.trim().isEmpty()) {
                conditions.add(Condition.of("$.deliveryStatus", "EQUALS", deliveryStatus));
            }
            if (reportId != null) {
                conditions.add(Condition.of("$.reportId", "EQUALS", reportId.toString()));
            }
            if (state != null && !state.trim().isEmpty()) {
                conditions.add(Condition.lifecycle("state", "EQUALS", state));
            }
            
            condition.setConditions(conditions);
            
            List<EntityResponse<EmailNotification>> emails = entityService.getItemsByCondition(
                EmailNotification.class,
                EmailNotification.ENTITY_NAME,
                EmailNotification.ENTITY_VERSION,
                condition,
                true
            );
            
            return ResponseEntity.ok(emails);
        } catch (Exception e) {
            logger.error("Failed to search email notifications: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<EntityResponse<EmailNotification>> updateEmailNotification(
            @PathVariable UUID id, 
            @RequestBody EmailNotification email,
            @RequestParam(required = false) String transition) {
        try {
            logger.info("Updating email notification with ID: {}, transition: {}", id, transition);
            
            EntityResponse<EmailNotification> response = entityService.update(id, email, transition);
            
            logger.info("Email notification updated with ID: {}", response.getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to update email notification {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEmailNotification(@PathVariable UUID id) {
        try {
            logger.info("Deleting email notification with ID: {}", id);
            entityService.deleteById(id);
            logger.info("Email notification deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Failed to delete email notification {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/{id}/transitions/{transitionName}")
    public ResponseEntity<EntityResponse<EmailNotification>> transitionEmailNotification(
            @PathVariable UUID id, 
            @PathVariable String transitionName) {
        try {
            logger.info("Transitioning email notification {} with transition: {}", id, transitionName);
            
            // Get current email notification
            EntityResponse<EmailNotification> currentResponse = entityService.getItem(id, EmailNotification.class);
            EmailNotification email = currentResponse.getData();
            
            // Update with transition
            EntityResponse<EmailNotification> response = entityService.update(id, email, transitionName);
            
            logger.info("Email notification transitioned with ID: {}, new state: {}", response.getId(), response.getState());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to transition email notification {} with {}: {}", id, transitionName, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/send-report-email")
    public ResponseEntity<EntityResponse<EmailNotification>> sendReportEmail(
            @RequestParam Long reportId,
            @RequestParam String recipientEmail,
            @RequestParam String subject,
            @RequestParam String bodyContent,
            @RequestParam(required = false) String attachmentPath) {
        try {
            logger.info("Creating email notification for report {} to {}", reportId, recipientEmail);
            
            EmailNotification email = new EmailNotification();
            email.setReportId(reportId);
            email.setRecipientEmail(recipientEmail);
            email.setSubject(subject);
            email.setBodyContent(bodyContent);
            email.setAttachmentPath(attachmentPath);
            email.setScheduledSendTime(LocalDateTime.now());
            email.setRetryCount(0);
            email.setMaxRetries(3);
            
            EntityResponse<EmailNotification> response = entityService.save(email);
            logger.info("Report email notification created with ID: {}", response.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            logger.error("Failed to create report email notification: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/by-business-id/{businessId}")
    public ResponseEntity<EntityResponse<EmailNotification>> getEmailNotificationByBusinessId(@PathVariable Long businessId) {
        try {
            logger.info("Retrieving email notification with business ID: {}", businessId);
            EntityResponse<EmailNotification> response = entityService.findByBusinessId(
                EmailNotification.class,
                EmailNotification.ENTITY_NAME,
                EmailNotification.ENTITY_VERSION,
                businessId.toString(),
                "id"
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to retrieve email notification by business ID {}: {}", businessId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @PutMapping("/by-business-id/{businessId}")
    public ResponseEntity<EntityResponse<EmailNotification>> updateEmailNotificationByBusinessId(
            @PathVariable Long businessId, 
            @RequestBody EmailNotification email,
            @RequestParam(required = false) String transition) {
        try {
            logger.info("Updating email notification with business ID: {}, transition: {}", businessId, transition);
            
            EntityResponse<EmailNotification> response = entityService.updateByBusinessId(email, "id", transition);
            
            logger.info("Email notification updated with business ID: {}", businessId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to update email notification by business ID {}: {}", businessId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
