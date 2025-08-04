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
@RequestMapping(path = "/mails")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    @PostMapping
    public ResponseEntity<Map<String, String>> createMail(@RequestBody Mail mail) {
        try {
            if (!mail.isValid()) {
                logger.error("Invalid mail entity data");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Mail.ENTITY_NAME,
                    ENTITY_VERSION,
                    mail
            );

            UUID technicalId = idFuture.get();

            // After adding, process mail
            processMail(technicalId, mail);

            return ResponseEntity.status(HttpStatus.CREATED).body(Collections.singletonMap("technicalId", technicalId.toString()));
        } catch (IllegalArgumentException e) {
            logger.error("Illegal argument: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            logger.error("Failed to create mail entity: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{technicalId}")
    public ResponseEntity<Map<String, Object>> getMailById(@PathVariable String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Mail.ENTITY_NAME,
                    ENTITY_VERSION,
                    id
            );

            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                logger.error("Mail entity not found for ID: {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            Mail mail = null;
            try {
                mail = entityService.getItem(Mail.ENTITY_NAME, ENTITY_VERSION, id).get()
                        .traverse().readValueAs(Mail.class);
            } catch (Exception ex) {
                // fallback: try to convert manually
                mail = new Mail();
                mail.setIsHappy(node.has("isHappy") ? node.get("isHappy").asBoolean() : null);
                mail.setMailList(node.has("mailList") && node.get("mailList").isArray() ? new ArrayList<>() : null);
                if (mail.getMailList() != null) {
                    node.get("mailList").forEach(n -> mail.getMailList().add(n.asText()));
                }
                mail.setContent(node.has("content") ? node.get("content").asText() : null);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("technicalId", technicalId);
            response.put("isHappy", mail.getIsHappy());
            response.put("mailList", mail.getMailList());
            response.put("content", mail.getContent());
            response.put("status", "UNKNOWN"); // status not stored anymore locally, no update methods available

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.error("Illegal argument: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            logger.error("Failed to get mail entity: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private void processMail(UUID technicalId, Mail mail) {
        // Step 1: Validation is already done before saving

        // Step 2: Check Happy Criteria
        boolean isHappy = checkMailHappyCriteria(mail);

        // Step 3: Check Gloomy Criteria (only if not happy)
        if (!isHappy) {
            isHappy = !checkMailGloomyCriteria(mail) ? false : false;
        }

        // Step 4: Set isHappy field in mail entity
        mail.setIsHappy(isHappy);

        // Step 5: Process sending mail based on classification
        try {
            if (isHappy) {
                sendHappyMail(mail);
                // todo: update status to COMPLETED in external service if supported
                logger.info("Sent happy mail for ID: {}", technicalId);
            } else {
                sendGloomyMail(mail);
                // todo: update status to COMPLETED in external service if supported
                logger.info("Sent gloomy mail for ID: {}", technicalId);
            }
        } catch (Exception e) {
            // todo: update status to FAILED in external service if supported
            logger.error("Failed to send mail for ID: {}. Error: {}", technicalId, e.getMessage());
        }
    }

    private boolean checkMailHappyCriteria(Mail mail) {
        String content = mail.getContent() != null ? mail.getContent().toLowerCase() : "";
        List<String> positiveKeywords = Arrays.asList("happy", "joy", "great", "good", "love", "excellent");
        return positiveKeywords.stream().anyMatch(content::contains);
    }

    private boolean checkMailGloomyCriteria(Mail mail) {
        String content = mail.getContent() != null ? mail.getContent().toLowerCase() : "";
        List<String> negativeKeywords = Arrays.asList("sad", "bad", "terrible", "hate", "gloomy", "angry");
        return negativeKeywords.stream().anyMatch(content::contains);
    }

    private void sendHappyMail(Mail mail) {
        for (String recipient : mail.getMailList()) {
            logger.info("Sending HAPPY mail to {} with content: {}", recipient, mail.getContent());
        }
    }

    private void sendGloomyMail(Mail mail) {
        for (String recipient : mail.getMailList()) {
            logger.info("Sending GLOOMY mail to {} with content: {}", recipient, mail.getContent());
        }
    }
}