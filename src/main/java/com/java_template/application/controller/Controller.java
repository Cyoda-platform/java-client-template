package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/mails")
@Slf4j
@AllArgsConstructor
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    // Use ENTITY_NAME constant from Mail class
    // Assuming Mail class exists and has public static ENTITY_NAME = "Mail"
    // For demonstration, let's assume Mail.ENTITY_NAME = "Mail"

    @PostMapping
    public ResponseEntity<Map<String, String>> createMail(@RequestBody Mail mail) {
        try {
            if (mail == null || !mail.isValid()) {
                logger.error("Invalid Mail entity received");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Mail.ENTITY_NAME,
                    ENTITY_VERSION,
                    mail
            );

            UUID technicalId = idFuture.get(5, TimeUnit.SECONDS);

            // Trigger processing
            processMail(technicalId.toString(), mail);

            logger.info("Mail created with technicalId: {}", technicalId);
            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId.toString());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            logger.error("IllegalArgumentException in createMail", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            logger.error("Exception in createMail", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{technicalId}")
    public ResponseEntity<MailStatusResponse> getMail(@PathVariable String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Mail.ENTITY_NAME,
                    ENTITY_VERSION,
                    uuid
            );

            ObjectNode node = itemFuture.get(5, TimeUnit.SECONDS);
            if (node == null) {
                logger.error("Mail not found with technicalId: {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            // Extract Mail data from ObjectNode
            // Assuming ObjectNode contains fields corresponding to Mail class
            Mail mail = new Mail();
            if (node.has("isHappy")) {
                mail.setIsHappy(node.get("isHappy").asBoolean());
            }
            if (node.has("mailList") && node.get("mailList").isArray()) {
                List<String> mailList = new ArrayList<>();
                node.get("mailList").forEach(jsonNode -> mailList.add(jsonNode.asText()));
                mail.setMailList(mailList);
            }

            MailStatusResponse response = new MailStatusResponse();
            response.setTechnicalId(technicalId);
            response.setIsHappy(mail.getIsHappy());
            response.setMailList(mail.getMailList());
            response.setStatus("COMPLETED"); // For prototype, assume completed after processing

            logger.info("Mail retrieved with technicalId: {}", technicalId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.error("IllegalArgumentException in getMail", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            logger.error("Exception in getMail", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Business logic for processing Mail entity
    private void processMail(String technicalId, Mail mail) {
        try {
            if (Boolean.TRUE.equals(mail.getIsHappy())) {
                sendHappyMail(mail.getMailList());
                logger.info("Processed sendHappyMail for technicalId: {}", technicalId);
            } else {
                sendGloomyMail(mail.getMailList());
                logger.info("Processed sendGloomyMail for technicalId: {}", technicalId);
            }
            // Completion and notification logic can be added here
        } catch (Exception e) {
            logger.error("Error processing mail with technicalId: {}", technicalId, e);
        }
    }

    private void sendHappyMail(List<String> recipients) {
        if (recipients == null) return;
        for (String recipient : recipients) {
            logger.info("Sending HAPPY mail to {}", recipient);
        }
    }

    private void sendGloomyMail(List<String> recipients) {
        if (recipients == null) return;
        for (String recipient : recipients) {
            logger.info("Sending GLOOMY mail to {}", recipient);
        }
    }

    // Response DTO for GET /mails/{technicalId}
    @Data
    public static class MailStatusResponse {
        private String technicalId;
        private Boolean isHappy;
        private List<String> mailList;
        private String status;
    }

    // Assuming Mail class with ENTITY_NAME and validation, getters/setters
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Mail {
        public static final String ENTITY_NAME = "Mail";

        private Boolean isHappy;
        private List<String> mailList;

        public boolean isValid() {
            // Basic validation: mailList not null and not empty
            return mailList != null && !mailList.isEmpty();
        }
    }
}