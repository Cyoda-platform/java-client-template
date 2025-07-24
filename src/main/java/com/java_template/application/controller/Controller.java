package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Mail;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/mail")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    @PostMapping("/mails")
    public ResponseEntity<?> createMail(@RequestBody Mail mailRequest) {
        try {
            if (mailRequest == null || mailRequest.getMailList() == null || mailRequest.getMailList().isEmpty()) {
                logger.error("Invalid mail creation request: mailList is required");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "mailList is required and cannot be empty"));
            }

            // Initialize mail entity
            Mail mail = new Mail();
            mail.setMailList(mailRequest.getMailList());
            mail.setIsHappy(null); // will be set by criteria processing
            mail.setStatus("CREATED");

            // Save to external service
            CompletableFuture<UUID> idFuture = entityService.addItem("Mail", ENTITY_VERSION, mail);
            UUID technicalId = idFuture.join();
            String technicalIdStr = technicalId.toString();

            // Process mail event-driven logic
            processMail(technicalId, mail);

            // Return only technicalId
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalIdStr));
        } catch (IllegalArgumentException e) {
            logger.error("Illegal argument: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error creating mail: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping("/mails/{technicalId}")
    public ResponseEntity<?> getMailById(@PathVariable String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem("Mail", ENTITY_VERSION, id);
            ObjectNode itemNode = itemFuture.join();
            if (itemNode == null || itemNode.isEmpty()) {
                logger.error("Mail not found for technicalId: {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Mail not found"));
            }
            // Convert ObjectNode to Mail
            Mail mail = entityService.getObjectMapper().treeToValue(itemNode, Mail.class);
            return ResponseEntity.ok(mail);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid technicalId format: {}", technicalId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid technicalId format"));
        } catch (Exception e) {
            logger.error("Error retrieving mail: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // Process method implementing business logic as per requirements
    private void processMail(UUID technicalId, Mail mail) {
        try {
            logger.info("Processing Mail with ID: {}", technicalId);

            // Step 2: Validation criteria
            boolean happyCriteriaMet = checkMailHappyCriteria(mail);
            boolean gloomyCriteriaMet = checkMailGloomyCriteria(mail);

            // Step 3: Classification based on criteria
            if (happyCriteriaMet) {
                mail.setIsHappy(true);
                mail.setStatus("PROCESSING");
                logger.info("Mail ID {} classified as Happy", technicalId);
                sendHappyMail(technicalId, mail);
            } else if (gloomyCriteriaMet) {
                mail.setIsHappy(false);
                mail.setStatus("PROCESSING");
                logger.info("Mail ID {} classified as Gloomy", technicalId);
                sendGloomyMail(technicalId, mail);
            } else {
                // If criteria not met, default to gloomy (or could reject)
                mail.setIsHappy(false);
                mail.setStatus("PROCESSING");
                logger.info("Mail ID {} did not meet happy criteria, classified as Gloomy by default", technicalId);
                sendGloomyMail(technicalId, mail);
            }

            // TODO: Update mail entity in external service instead of local cache
            // Since update operations not supported, skipping update call here

        } catch (Exception e) {
            logger.error("Error processing mail ID {}: {}", technicalId, e.getMessage(), e);
        }
    }

    // Criteria checks - add real logic as needed
    private boolean checkMailHappyCriteria(Mail mail) {
        // Example: happy if mailList size is even (placeholder logic)
        return mail.getMailList().size() % 2 == 0;
    }

    private boolean checkMailGloomyCriteria(Mail mail) {
        // Example: gloomy if mailList size is odd (placeholder logic)
        return mail.getMailList().size() % 2 != 0;
    }

    // Processor for happy mail
    private void sendHappyMail(UUID technicalId, Mail mail) {
        try {
            // Simulate sending happy mail with different template/content
            logger.info("Sending Happy Mail to: {}", mail.getMailList());
            // Simulate success
            mail.setStatus("SENT");
            logger.info("Happy Mail sent successfully for ID: {}", technicalId);
        } catch (Exception e) {
            mail.setStatus("FAILED");
            logger.error("Failed to send Happy Mail for ID: {}, error: {}", technicalId, e.getMessage());
        }
        // TODO: Update mail entity in external service instead of local cache
    }

    // Processor for gloomy mail
    private void sendGloomyMail(UUID technicalId, Mail mail) {
        try {
            // Simulate sending gloomy mail with different template/content
            logger.info("Sending Gloomy Mail to: {}", mail.getMailList());
            // Simulate success
            mail.setStatus("SENT");
            logger.info("Gloomy Mail sent successfully for ID: {}", technicalId);
        } catch (Exception e) {
            mail.setStatus("FAILED");
            logger.error("Failed to send Gloomy Mail for ID: {}, error: {}", technicalId, e.getMessage());
        }
        // TODO: Update mail entity in external service instead of local cache
    }
}