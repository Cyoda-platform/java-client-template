package com.java_template.application.controller;

import com.java_template.application.entity.mail.version_1.Mail;
import com.java_template.common.dto.EntityResponse;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/mails")
public class MailController {

    private static final Logger logger = LoggerFactory.getLogger(MailController.class);

    @Autowired
    private EntityService entityService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> createMail(@RequestBody Mail mail) {
        try {
            logger.info("Creating new mail entity");
            
            // Validate input
            if (mail.getIsHappy() == null) {
                return ResponseEntity.badRequest().body(createErrorResponse("VALIDATION_ERROR", "isHappy field is required"));
            }
            if (mail.getMailList() == null || mail.getMailList().isEmpty()) {
                return ResponseEntity.badRequest().body(createErrorResponse("VALIDATION_ERROR", "mailList field is required and cannot be empty"));
            }
            
            // Validate email addresses
            for (String email : mail.getMailList()) {
                if (!isValidEmail(email)) {
                    Map<String, Object> errorDetails = new HashMap<>();
                    errorDetails.put("field", "mailList");
                    errorDetails.put("value", email);
                    return ResponseEntity.badRequest().body(createErrorResponse("VALIDATION_ERROR", "Invalid email address in mailList", errorDetails));
                }
            }

            EntityResponse<Mail> response = entityService.save(mail);
            return ResponseEntity.ok(convertToApiResponse(response));
        } catch (Exception e) {
            logger.error("Error creating mail: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("INTERNAL_ERROR", "Failed to create mail"));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getMailById(@PathVariable UUID id) {
        try {
            logger.info("Getting mail by ID: {}", id);
            EntityResponse<Mail> response = entityService.getItem(id, Mail.class);
            return ResponseEntity.ok(convertToApiResponse(response));
        } catch (Exception e) {
            logger.error("Error getting mail by ID {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(createErrorResponse("MAIL_NOT_FOUND", "Mail with ID " + id + " not found"));
        }
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAllMails(
            @RequestParam(required = false) String state,
            @RequestParam(required = false) Boolean isHappy) {
        try {
            logger.info("Getting all mails with filters - state: {}, isHappy: {}", state, isHappy);
            
            List<EntityResponse<Mail>> responses;
            
            if (state != null || isHappy != null) {
                // Build search condition
                List<Condition> conditions = new ArrayList<>();
                
                if (state != null) {
                    // Note: state filtering would need to be implemented based on metadata
                    logger.warn("State filtering not yet implemented in this version");
                }
                
                if (isHappy != null) {
                    Condition isHappyCondition = Condition.of("$.isHappy", "EQUALS", isHappy);
                    conditions.add(isHappyCondition);
                }
                
                if (!conditions.isEmpty()) {
                    SearchConditionRequest condition = new SearchConditionRequest();
                    condition.setType("group");
                    condition.setOperator("AND");
                    condition.setConditions(conditions);
                    
                    responses = entityService.getItemsByCondition(Mail.class, Mail.ENTITY_NAME, Mail.ENTITY_VERSION, condition, true);
                } else {
                    responses = entityService.findAll(Mail.class, Mail.ENTITY_NAME, Mail.ENTITY_VERSION);
                }
            } else {
                responses = entityService.findAll(Mail.class, Mail.ENTITY_NAME, Mail.ENTITY_VERSION);
            }
            
            List<Map<String, Object>> result = responses.stream()
                    .map(this::convertToApiResponse)
                    .collect(Collectors.toList());
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error getting all mails: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Collections.emptyList());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateMail(
            @PathVariable UUID id,
            @RequestBody Mail mail,
            @RequestParam(required = false) String transition) {
        try {
            logger.info("Updating mail with ID: {}, transition: {}", id, transition);
            
            // Validate input
            if (mail.getIsHappy() == null) {
                return ResponseEntity.badRequest().body(createErrorResponse("VALIDATION_ERROR", "isHappy field is required"));
            }
            if (mail.getMailList() == null || mail.getMailList().isEmpty()) {
                return ResponseEntity.badRequest().body(createErrorResponse("VALIDATION_ERROR", "mailList field is required and cannot be empty"));
            }
            
            // Validate email addresses
            for (String email : mail.getMailList()) {
                if (!isValidEmail(email)) {
                    Map<String, Object> errorDetails = new HashMap<>();
                    errorDetails.put("field", "mailList");
                    errorDetails.put("value", email);
                    return ResponseEntity.badRequest().body(createErrorResponse("VALIDATION_ERROR", "Invalid email address in mailList", errorDetails));
                }
            }

            EntityResponse<Mail> response = entityService.update(id, mail, transition);
            return ResponseEntity.ok(convertToApiResponse(response));
        } catch (Exception e) {
            logger.error("Error updating mail with ID {}: {}", id, e.getMessage(), e);
            if (e.getMessage().contains("not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(createErrorResponse("MAIL_NOT_FOUND", "Mail with ID " + id + " not found"));
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("INTERNAL_ERROR", "Failed to update mail"));
        }
    }

    @PostMapping("/{id}/transitions/{transitionName}")
    public ResponseEntity<Map<String, Object>> triggerTransition(
            @PathVariable UUID id,
            @PathVariable String transitionName) {
        try {
            logger.info("Triggering transition {} for mail ID: {}", transitionName, id);
            
            // Get current entity
            EntityResponse<Mail> currentResponse = entityService.getItem(id, Mail.class);
            Mail currentMail = currentResponse.getData();
            
            // Validate transition (only retry_processing is allowed manually)
            if (!"retry_processing".equals(transitionName)) {
                Map<String, Object> errorDetails = new HashMap<>();
                errorDetails.put("currentState", currentResponse.getMetadata().getState());
                errorDetails.put("requestedTransition", transitionName);
                errorDetails.put("availableTransitions", Arrays.asList("retry_processing"));
                
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(createErrorResponse("INVALID_TRANSITION", 
                                "Cannot execute transition '" + transitionName + "' from current state '" + 
                                currentResponse.getMetadata().getState() + "'", errorDetails));
            }
            
            EntityResponse<Mail> response = entityService.update(id, currentMail, transitionName);
            return ResponseEntity.ok(convertToApiResponse(response));
        } catch (Exception e) {
            logger.error("Error triggering transition {} for mail ID {}: {}", transitionName, id, e.getMessage(), e);
            if (e.getMessage().contains("not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(createErrorResponse("MAIL_NOT_FOUND", "Mail with ID " + id + " not found"));
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("INTERNAL_ERROR", "Failed to trigger transition"));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteMail(@PathVariable UUID id) {
        try {
            logger.info("Deleting mail with ID: {}", id);
            UUID deletedId = entityService.deleteById(id);
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Mail deleted successfully");
            response.put("id", deletedId.toString());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error deleting mail with ID {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(createErrorResponse("MAIL_NOT_FOUND", "Mail with ID " + id + " not found"));
        }
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> getMailStatus(@PathVariable UUID id) {
        try {
            logger.info("Getting status for mail ID: {}", id);
            EntityResponse<Mail> response = entityService.getItem(id, Mail.class);
            
            Map<String, Object> statusResponse = new HashMap<>();
            statusResponse.put("id", id.toString());
            statusResponse.put("currentState", response.getMetadata().getState());
            
            // Determine available transitions based on current state
            List<String> availableTransitions = getAvailableTransitions(response.getMetadata().getState());
            statusResponse.put("availableTransitions", availableTransitions);
            
            // Note: lastTransition and lastTransitionAt would need additional metadata support
            statusResponse.put("lastTransition", "N/A");
            statusResponse.put("lastTransitionAt", response.getCreationDate());
            
            return ResponseEntity.ok(statusResponse);
        } catch (Exception e) {
            logger.error("Error getting status for mail ID {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(createErrorResponse("MAIL_NOT_FOUND", "Mail with ID " + id + " not found"));
        }
    }

    private Map<String, Object> convertToApiResponse(EntityResponse<Mail> response) {
        Map<String, Object> apiResponse = new HashMap<>();
        apiResponse.put("id", response.getMetadata().getId().toString());
        apiResponse.put("isHappy", response.getData().getIsHappy());
        apiResponse.put("mailList", response.getData().getMailList());
        apiResponse.put("state", response.getMetadata().getState());
        apiResponse.put("createdAt", response.getCreationDate());
        apiResponse.put("updatedAt", response.getCreationDate());
        return apiResponse;
    }

    private Map<String, Object> createErrorResponse(String error, String message) {
        return createErrorResponse(error, message, null);
    }

    private Map<String, Object> createErrorResponse(String error, String message, Map<String, Object> details) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", error);
        errorResponse.put("message", message);
        if (details != null) {
            errorResponse.put("details", details);
        }
        return errorResponse;
    }

    private boolean isValidEmail(String email) {
        return email != null && email.matches("^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$");
    }

    private List<String> getAvailableTransitions(String currentState) {
        switch (currentState) {
            case "failed":
                return Arrays.asList("retry_processing");
            case "processing_happy":
                return Arrays.asList("happy_mail_sent", "happy_mail_failed");
            case "processing_gloomy":
                return Arrays.asList("gloomy_mail_sent", "gloomy_mail_failed");
            case "sent":
                return Collections.emptyList();
            default:
                return Collections.emptyList();
        }
    }
}
