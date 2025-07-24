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

import java.util.List;
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

    @PostMapping
    public ResponseEntity<?> createMail(@RequestBody Mail mail) {
        try {
            if (mail == null) {
                logger.error("Received null mail in createMail");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Mail cannot be null"));
            }
            if (mail.getMailList() == null || mail.getMailList().isEmpty()) {
                logger.error("Mail list is empty or null");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "mailList must contain at least one email address"));
            }
            for (String email : mail.getMailList()) {
                if (email == null || email.isBlank()) {
                    logger.error("Invalid email in mailList: '{}'", email);
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(Map.of("error", "mailList contains blank or null email"));
                }
            }

            // addItem returns CompletableFuture<UUID>
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    "mail",
                    ENTITY_VERSION,
                    mail
            );

            UUID technicalId = idFuture.join(); // blocking here for simplicity

            // Set id field for internal use (string form of UUID)
            mail.setId(technicalId.toString());

            logger.info("Mail entity created with ID: {}", technicalId);

            // Trigger processing
            processMail(mail);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("technicalId", technicalId.toString()));

        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument in createMail", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Exception in createMail", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getMailById(@PathVariable("id") String id) {
        try {
            UUID technicalId;
            try {
                technicalId = UUID.fromString(id);
            } catch (IllegalArgumentException e) {
                logger.error("Invalid UUID format for id: {}", id);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Invalid technicalId format"));
            }

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    "mail",
                    ENTITY_VERSION,
                    technicalId
            );

            ObjectNode node = itemFuture.join(); // blocking here

            if (node == null || node.isEmpty()) {
                logger.error("Mail not found with ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Mail not found for technicalId: " + id));
            }

            // Convert ObjectNode to Mail
            Mail mail = null;
            try {
                mail = com.fasterxml.jackson.databind.json.JsonMapper.builder()
                        .findAndAddModules()
                        .build()
                        .treeToValue(node, Mail.class);
            } catch (Exception e) {
                logger.error("Failed to deserialize mail entity", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Failed to parse mail entity"));
            }

            return ResponseEntity.ok(mail);

        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument in getMailById", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Exception in getMailById", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error"));
        }
    }

    // Core business logic as per event-driven architecture
    private void processMail(Mail mail) {
        logger.info("Processing Mail with ID: {}", mail.getId());

        // Validation criteria (if explicitly requested - here always executed)
        boolean isHappyValid = checkMailHappy(mail);
        boolean isGloomyValid = checkMailGloomy(mail);

        // Routing based on criteria results
        if (isHappyValid) {
            processMailSendHappyMail(mail);
        } else if (isGloomyValid) {
            processMailSendGloomyMail(mail);
        } else {
            logger.error("Mail with ID {} does not meet any criteria for processing", mail.getId());
        }
    }

    private boolean checkMailHappy(Mail mail) {
        // Criteria: isHappy == true
        boolean result = Boolean.TRUE.equals(mail.getIsHappy());
        logger.info("checkMailHappy for ID {}: {}", mail.getId(), result);
        return result;
    }

    private boolean checkMailGloomy(Mail mail) {
        // Criteria: isHappy == false
        boolean result = Boolean.FALSE.equals(mail.getIsHappy());
        logger.info("checkMailGloomy for ID {}: {}", mail.getId(), result);
        return result;
    }

    private void processMailSendHappyMail(Mail mail) {
        // Business logic: send happy mail
        logger.info("Sending HAPPY mail to recipients: {} for Mail ID: {}", mail.getMailList(), mail.getId());

        // Simulate sending mail - in real app, integrate with mail service
        for (String recipient : mail.getMailList()) {
            logger.info("Happy mail sent to {}", recipient);
        }
    }

    private void processMailSendGloomyMail(Mail mail) {
        // Business logic: send gloomy mail
        logger.info("Sending GLOOMY mail to recipients: {} for Mail ID: {}", mail.getMailList(), mail.getId());

        // Simulate sending mail - in real app, integrate with mail service
        for (String recipient : mail.getMailList()) {
            logger.info("Gloomy mail sent to {}", recipient);
        }
    }
}