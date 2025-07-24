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

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/mail")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private static final String ENTITY_NAME = "Mail";

    private final EntityService entityService;
    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    @PostMapping
    public ResponseEntity<?> createMail(@RequestBody Mail mail) {
        try {
            // Validate input fields required for creation
            if (mail.getMailList() == null || mail.getMailList().isEmpty()) {
                logger.error("Mail creation failed: mailList is empty");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "mailList must not be empty"));
            }
            for (String email : mail.getMailList()) {
                if (email == null || email.isBlank()) {
                    logger.error("Mail creation failed: mailList contains blank email");
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "mailList contains invalid email"));
                }
            }
            if (mail.getContent() == null || mail.getContent().isBlank()) {
                logger.error("Mail creation failed: content is blank");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "content must not be blank"));
            }

            mail.setStatus("PENDING"); // initial status

            // Call entityService to add item
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    ENTITY_NAME,
                    ENTITY_VERSION,
                    mail
            );
            UUID technicalId = idFuture.get();

            logger.info("Mail created with technicalId {}", technicalId);

            // Trigger processing event asynchronously
            processMail(technicalId, mail);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId.toString()));
        } catch (IllegalArgumentException e) {
            logger.error("Illegal argument during mail creation: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Failed to create mail: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getMailById(@PathVariable String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    ENTITY_NAME,
                    ENTITY_VERSION,
                    uuid
            );
            ObjectNode node = itemFuture.get();
            if (node == null) {
                logger.error("Mail not found with technicalId {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Mail not found"));
            }

            // Map ObjectNode to Mail fields for response (including technicalId)
            Map<String, Object> response = new HashMap<>();
            response.put("technicalId", node.get("technicalId").asText());

            // Extract fields safely
            if (node.has("isHappy")) response.put("isHappy", node.get("isHappy").asBoolean());
            if (node.has("mailList")) {
                // mailList is expected to be an array of Strings
                List<String> mailList = new ArrayList<>();
                node.withArray("mailList").forEach(jsonNode -> mailList.add(jsonNode.asText()));
                response.put("mailList", mailList);
            }
            if (node.has("content")) response.put("content", node.get("content").asText());
            if (node.has("status")) response.put("status", node.get("status").asText());

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid technicalId format: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid technicalId format"));
        } catch (ExecutionException ee) {
            if (ee.getCause() instanceof NoSuchElementException) {
                logger.error("Mail not found with technicalId {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Mail not found"));
            }
            logger.error("Execution exception: {}", ee.getMessage(), ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        } catch (Exception e) {
            logger.error("Failed to get mail by id: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    private void processMail(UUID technicalId, Mail mail) {
        logger.info("Processing Mail with ID: {}", technicalId);

        // Step 1: Validation already done on creation

        // Step 2: Criteria Evaluation - simple keyword-based sentiment check (example)
        boolean isHappy = evaluateMailContentForHappiness(mail.getContent());
        mail.setIsHappy(isHappy);

        // Step 3: Processing based on isHappy flag
        try {
            if (isHappy) {
                sendHappyMail(mail);
                mail.setStatus("SENT_HAPPY");
                logger.info("Mail {} sent as happy mail", technicalId);
            } else {
                sendGloomyMail(mail);
                mail.setStatus("SENT_GLOOMY");
                logger.info("Mail {} sent as gloomy mail", technicalId);
            }
        } catch (Exception e) {
            mail.setStatus("FAILED");
            logger.error("Failed to send mail {}: {}", technicalId, e.getMessage());
        }

        // TODO: Update entity in external service - update operation not supported, so skip
        // Log current mail state for visibility
        logger.info("Mail processing completed for id {} with status {} and isHappy {}", technicalId, mail.getStatus(), mail.getIsHappy());
    }

    private boolean evaluateMailContentForHappiness(String content) {
        String lowerContent = content.toLowerCase(Locale.ROOT);
        List<String> positiveKeywords = Arrays.asList("happy", "wonderful", "great", "joy", "love");
        for (String keyword : positiveKeywords) {
            if (lowerContent.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private void sendHappyMail(Mail mail) {
        for (String recipient : mail.getMailList()) {
            logger.info("Sending HAPPY mail to {}", recipient);
            // Real mail sending logic would go here
        }
    }

    private void sendGloomyMail(Mail mail) {
        for (String recipient : mail.getMailList()) {
            logger.info("Sending GLOOMY mail to {}", recipient);
            // Real mail sending logic would go here
        }
    }
}