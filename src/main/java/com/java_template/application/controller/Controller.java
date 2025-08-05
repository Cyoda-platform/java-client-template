package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
@RequestMapping(path = "/entity")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    @PostMapping("/mails")
    public ResponseEntity<?> createMail(@RequestBody Mail mail) {
        try {
            if (mail == null || !mail.isValid()) {
                logger.error("Invalid Mail entity received");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid Mail entity"));
            }
            CompletableFuture<UUID> idFuture = entityService.addItem(Mail.ENTITY_NAME, ENTITY_VERSION, mail);
            UUID technicalId = idFuture.join();
            logger.info("Mail entity created with technicalId: {}", technicalId);
            processMail(technicalId.toString(), mail);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId.toString()));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument in createMail: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Failed to create Mail entity: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping("/mails/{technicalId}")
    public ResponseEntity<?> getMailById(@PathVariable String technicalId) {
        try {
            UUID techId = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Mail.ENTITY_NAME, ENTITY_VERSION, techId);
            ObjectNode node = itemFuture.join();
            if (node == null || node.isEmpty()) {
                logger.error("Mail entity not found for technicalId: {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Mail not found"));
            }
            // deserialize ObjectNode to Mail
            Mail mail = JsonUtils.convert(node, Mail.class);
            Map<String, Object> response = Map.of(
                    "technicalId", technicalId,
                    "isHappy", mail.isHappy(),
                    "mailList", mail.getMailList(),
                    "status", mail.getStatus() == null ? "PENDING" : mail.getStatus()
            );
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid technicalId format: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid technicalId format"));
        } catch (Exception e) {
            logger.error("Failed to retrieve Mail entity: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // Business logic processing for Mail entity
    private void processMail(String technicalId, Mail mail) {
        logger.info("Processing Mail entity with technicalId: {}", technicalId);
        try {
            boolean criteriaResult = checkMailIsHappy(mail);
            if (criteriaResult) {
                sendHappyMail(mail);
                mail.setStatus("COMPLETED");
                logger.info("Happy mail sent for technicalId: {}", technicalId);
            } else {
                sendGloomyMail(mail);
                mail.setStatus("COMPLETED");
                logger.info("Gloomy mail sent for technicalId: {}", technicalId);
            }
        } catch (Exception e) {
            mail.setStatus("FAILED");
            logger.error("Failed to process mail with technicalId: {}. Error: {}", technicalId, e.getMessage());
        }
    }

    // Criteria check method
    private boolean checkMailIsHappy(Mail mail) {
        return mail.isHappy();
    }

    // Processor for sending happy mails
    private void sendHappyMail(Mail mail) {
        for (String recipient : mail.getMailList()) {
            logger.info("Sending HAPPY mail to {}", recipient);
        }
    }

    // Processor for sending gloomy mails
    private void sendGloomyMail(Mail mail) {
        for (String recipient : mail.getMailList()) {
            logger.info("Sending GLOOMY mail to {}", recipient);
        }
    }

}