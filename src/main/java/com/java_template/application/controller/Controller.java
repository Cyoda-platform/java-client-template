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
import java.util.UUID;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/mail")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private final EntityService entityService;
    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    @PostMapping
    public ResponseEntity<Map<String, String>> createMail(@RequestBody Mail mail) {
        try {
            if (mail == null || mail.getMailList() == null || mail.getMailList().isEmpty()) {
                logger.error("Invalid mail creation request: mail or mailList is null/empty");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            CompletableFuture<UUID> idFuture = entityService.addItem(Mail.ENTITY_NAME, ENTITY_VERSION, mail);
            UUID technicalId = idFuture.get();

            logger.info("Mail created with technicalId {}", technicalId);

            // Trigger processing for the newly created mail entity
            processMail(technicalId, mail);

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId.toString());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            logger.error("Illegal argument exception during mail creation: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Execution exception during mail creation: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected exception during mail creation: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{technicalId}")
    public ResponseEntity<Mail> getMailById(@PathVariable String technicalId) {
        try {
            UUID technicalUUID = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Mail.ENTITY_NAME, ENTITY_VERSION, technicalUUID);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                logger.error("Mail not found with technicalId {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Mail mail = node.traverse().readValueAs(Mail.class);
            logger.info("Mail retrieved with technicalId {}", technicalId);
            return ResponseEntity.ok(mail);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid technicalId format: {}", technicalId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Execution exception during retrieving mail: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected exception during retrieving mail: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping
    public ResponseEntity<List<Mail>> getMailsByIsHappy(@RequestParam(required = false) Boolean isHappy) {
        try {
            if (isHappy == null) {
                CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(Mail.ENTITY_NAME, ENTITY_VERSION);
                ArrayNode nodes = itemsFuture.get();
                List<Mail> mails = new ArrayList<>();
                if (nodes != null) {
                    for (int i = 0; i < nodes.size(); i++) {
                        Mail mail = nodes.get(i).traverse().readValueAs(Mail.class);
                        mails.add(mail);
                    }
                }
                logger.info("Retrieving all mails, count {}", mails.size());
                return ResponseEntity.ok(mails);
            } else {
                Condition condition = Condition.of("$.isHappy", "EQUALS", isHappy);
                SearchConditionRequest conditionRequest = SearchConditionRequest.group("AND", condition);
                CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                        Mail.ENTITY_NAME, ENTITY_VERSION, conditionRequest, true);
                ArrayNode nodes = filteredItemsFuture.get();
                List<Mail> mails = new ArrayList<>();
                if (nodes != null) {
                    for (int i = 0; i < nodes.size(); i++) {
                        Mail mail = nodes.get(i).traverse().readValueAs(Mail.class);
                        mails.add(mail);
                    }
                }
                logger.info("Retrieving mails filtered by isHappy={}, count {}", isHappy, mails.size());
                return ResponseEntity.ok(mails);
            }
        } catch (IllegalArgumentException e) {
            logger.error("Illegal argument exception during mail retrieval: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Execution exception during mail retrieval: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected exception during mail retrieval: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private void processMail(UUID technicalId, Mail mail) {
        // Validation phase - could invoke criteria checks if implemented
        boolean isHappy = Boolean.TRUE.equals(mail.getIsHappy());

        // Business logic for happy mails
        if (isHappy) {
            boolean happySent = sendHappyMail(mail.getMailList());
            if (happySent) {
                logger.info("Happy mail sent successfully for technicalId {}", technicalId);
            } else {
                logger.error("Failed to send happy mail for technicalId {}", technicalId);
            }
        }
        // Business logic for gloomy mails
        else {
            boolean gloomySent = sendGloomyMail(mail.getMailList());
            if (gloomySent) {
                logger.info("Gloomy mail sent successfully for technicalId {}", technicalId);
            } else {
                logger.error("Failed to send gloomy mail for technicalId {}", technicalId);
            }
        }
        // Completion and notification could be expanded here if needed
    }

    private boolean sendHappyMail(List<String> mailList) {
        try {
            for (String recipient : mailList) {
                logger.info("Sending happy mail to {}", recipient);
                // Here, integrate with actual mail sending service
            }
            return true;
        } catch (Exception e) {
            logger.error("Exception while sending happy mail: {}", e.getMessage());
            return false;
        }
    }

    private boolean sendGloomyMail(List<String> mailList) {
        try {
            for (String recipient : mailList) {
                logger.info("Sending gloomy mail to {}", recipient);
                // Here, integrate with actual mail sending service
            }
            return true;
        } catch (Exception e) {
            logger.error("Exception while sending gloomy mail: {}", e.getMessage());
            return false;
        }
    }
}