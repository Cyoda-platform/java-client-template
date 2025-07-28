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
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/mail")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);
    private final EntityService entityService;
    private static final String ENTITY_NAME = "Mail";

    @PostMapping
    public ResponseEntity<?> createMail(@RequestBody Mail mail) {
        try {
            if (mail == null) {
                logger.error("Received null Mail object");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Mail object is required"));
            }
            if (!mail.isValid()) {
                logger.error("Invalid Mail entity: missing required fields");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid Mail entity: mailList, content, and isHappy are required"));
            }

            mail.setStatus("CREATED");
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    ENTITY_NAME,
                    ENTITY_VERSION,
                    mail
            );
            UUID technicalId = idFuture.get();

            try {
                processMail(technicalId.toString(), mail);
                logger.info("Successfully processed Mail with ID {}", technicalId);
            } catch (Exception e) {
                logger.error("Failed to process Mail with ID {}: {}", technicalId, e.getMessage());
                mail.setStatus("FAILED");
                // TODO: consider update operation if supported
            }

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId.toString()));
        } catch (IllegalArgumentException e) {
            logger.error("IllegalArgumentException in createMail: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error in createMail: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getMailById(@PathVariable String technicalId) {
        try {
            UUID uuid;
            try {
                uuid = UUID.fromString(technicalId);
            } catch (IllegalArgumentException e) {
                logger.error("Invalid UUID format: {}", technicalId);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid technicalId format"));
            }

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(ENTITY_NAME, ENTITY_VERSION, uuid);
            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                logger.error("Mail with ID {} not found", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Mail not found"));
            }

            // Convert ObjectNode to Mail
            Mail mail = convertObjectNodeToMail(node);
            return ResponseEntity.ok(mail);
        } catch (IllegalArgumentException e) {
            logger.error("IllegalArgumentException in getMailById: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error in getMailById: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping
    public ResponseEntity<?> getMailByCondition(@RequestParam(required = false) Boolean isHappy) {
        try {
            if (isHappy == null) {
                CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(ENTITY_NAME, ENTITY_VERSION);
                ArrayNode arrayNode = itemsFuture.get();
                List<Mail> mails = new ArrayList<>();
                for (var node : arrayNode) {
                    Mail mail = convertObjectNodeToMail((ObjectNode) node);
                    mails.add(mail);
                }
                return ResponseEntity.ok(mails);
            } else {
                Condition condition = Condition.of("$.isHappy", "EQUALS", isHappy);
                SearchConditionRequest condReq = SearchConditionRequest.group("AND", condition);
                CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, condReq, true);
                ArrayNode arrayNode = filteredItemsFuture.get();
                List<Mail> mails = new ArrayList<>();
                for (var node : arrayNode) {
                    Mail mail = convertObjectNodeToMail((ObjectNode) node);
                    mails.add(mail);
                }
                return ResponseEntity.ok(mails);
            }
        } catch (IllegalArgumentException e) {
            logger.error("IllegalArgumentException in getMailByCondition: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error in getMailByCondition: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    private void processMail(String technicalId, Mail mail) {
        if (mail.getMailList() == null || mail.getMailList().isEmpty()) {
            logger.error("Mail ID {} has empty mailList", technicalId);
            mail.setStatus("FAILED");
            return;
        }

        if (mail.getContent() == null || mail.getContent().isBlank()) {
            logger.error("Mail ID {} has empty content", technicalId);
            mail.setStatus("FAILED");
            return;
        }

        if (mail.getIsHappy() == null) {
            logger.error("Mail ID {} has null isHappy field", technicalId);
            mail.setStatus("FAILED");
            return;
        }

        try {
            if (Boolean.TRUE.equals(mail.getIsHappy())) {
                sendHappyMail(technicalId, mail);
                mail.setStatus("SENT_HAPPY");
                logger.info("Mail ID {} sent as happy mail", technicalId);
            } else {
                sendGloomyMail(technicalId, mail);
                mail.setStatus("SENT_GLOOMY");
                logger.info("Mail ID {} sent as gloomy mail", technicalId);
            }
        } catch (Exception e) {
            mail.setStatus("FAILED");
            logger.error("Error processing Mail ID {}: {}", technicalId, e.getMessage());
        }
    }

    private void sendHappyMail(String technicalId, Mail mail) {
        logger.info("Sending happy mail to recipients: {}", mail.getMailList());
        // TODO: integrate actual sending mechanism
    }

    private void sendGloomyMail(String technicalId, Mail mail) {
        logger.info("Sending gloomy mail to recipients: {}", mail.getMailList());
        // TODO: integrate actual sending mechanism
    }

    private Mail convertObjectNodeToMail(ObjectNode node) {
        try {
            Mail mail = new Mail();
            if (node.has("mailList")) {
                var mailListNode = node.get("mailList");
                if (mailListNode.isArray()) {
                    List<String> mailList = new ArrayList<>();
                    mailListNode.forEach(n -> mailList.add(n.asText()));
                    mail.setMailList(mailList);
                }
            }
            if (node.has("content")) {
                mail.setContent(node.get("content").asText());
            }
            if (node.has("isHappy")) {
                if (node.get("isHappy").isBoolean()) {
                    mail.setIsHappy(node.get("isHappy").asBoolean());
                } else if (node.get("isHappy").isTextual()) {
                    mail.setIsHappy(Boolean.valueOf(node.get("isHappy").asText()));
                }
            }
            if (node.has("status")) {
                mail.setStatus(node.get("status").asText());
            }
            // Optionally set other fields if any

            return mail;
        } catch (Exception e) {
            logger.error("Failed to convert ObjectNode to Mail: {}", e.getMessage(), e);
            return new Mail(); // return empty mail in error case
        }
    }
}