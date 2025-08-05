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
import java.util.concurrent.TimeoutException;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/mail")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    // Keep AtomicLong for local id generation to generate some id string for response (optional)
    private final AtomicLong mailIdCounter = new AtomicLong(1);

    @PostMapping
    public ResponseEntity<Map<String, String>> createMail(@RequestBody Mail mail) {
        try {
            if (mail == null || !mail.isValid()) {
                logger.error("Invalid Mail entity received");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            mail.setStatus("PENDING");

            // call entityService.addItem asynchronously
            CompletableFuture<UUID> idFuture = entityService.addItem(Mail.ENTITY_NAME, ENTITY_VERSION, mail);
            UUID technicalId = idFuture.get();

            String technicalIdStr = technicalId.toString();

            logger.info("Mail entity created with technicalId: {}", technicalIdStr);

            processMail(technicalIdStr, mail);

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalIdStr);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            logger.error("IllegalArgumentException in createMail: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            logger.error("Exception in createMail: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{technicalId}")
    public ResponseEntity<Mail> getMailById(@PathVariable String technicalId) {
        try {
            UUID technicalUUID = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Mail.ENTITY_NAME, ENTITY_VERSION, technicalUUID);
            ObjectNode node = itemFuture.get();

            if (node == null || node.isEmpty()) {
                logger.error("Mail entity not found for technicalId: {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            // Convert ObjectNode to Mail entity
            Mail mail = node.traverse().readValueAs(Mail.class);
            return ResponseEntity.ok(mail);

        } catch (IllegalArgumentException e) {
            logger.error("IllegalArgumentException in getMailById: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            logger.error("Exception in getMailById: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private void processMail(String technicalId, Mail mail) {
        logger.info("Processing Mail entity with technicalId: {}", technicalId);

        // Validate mail fields (already partially validated in isValid)
        if (mail.getMailList() == null || mail.getMailList().isEmpty()) {
            logger.error("MailList is empty for Mail with technicalId: {}", technicalId);
            mail.setStatus("FAILED");
            updateMailStatus(technicalId, mail);
            return;
        }
        if (mail.getSubject() == null || mail.getSubject().isBlank()) {
            logger.error("Subject is blank for Mail with technicalId: {}", technicalId);
            mail.setStatus("FAILED");
            updateMailStatus(technicalId, mail);
            return;
        }
        if (mail.getContent() == null || mail.getContent().isBlank()) {
            logger.error("Content is blank for Mail with technicalId: {}", technicalId);
            mail.setStatus("FAILED");
            updateMailStatus(technicalId, mail);
            return;
        }

        try {
            if (Boolean.TRUE.equals(mail.getIsHappy())) {
                if (checkMailHappy(mail)) {
                    sendHappyMail(mail);
                    mail.setStatus("SENT");
                } else {
                    logger.error("Mail did not pass happy criteria for technicalId: {}", technicalId);
                    mail.setStatus("FAILED");
                }
            } else {
                if (checkMailGloomy(mail)) {
                    sendGloomyMail(mail);
                    mail.setStatus("SENT");
                } else {
                    logger.error("Mail did not pass gloomy criteria for technicalId: {}", technicalId);
                    mail.setStatus("FAILED");
                }
            }
        } catch (Exception e) {
            logger.error("Exception during mail processing for technicalId: {}: {}", technicalId, e.getMessage());
            mail.setStatus("FAILED");
        }

        updateMailStatus(technicalId, mail);
        logger.info("Processing completed for Mail with technicalId: {} with status: {}", technicalId, mail.getStatus());
    }

    private void updateMailStatus(String technicalId, Mail mail) {
        // TODO: No update operation available in EntityService. Implement update logic or leave as TODO.
        // Current workaround: log status update only.
        logger.info("Updating Mail status for technicalId: {} to {}", technicalId, mail.getStatus());
        // Ideally, should call entityService update method here, but it's not supported.
    }

    private boolean checkMailHappy(Mail mail) {
        // Criteria: isHappy == true
        return Boolean.TRUE.equals(mail.getIsHappy());
    }

    private boolean checkMailGloomy(Mail mail) {
        // Criteria: isHappy == false
        return Boolean.FALSE.equals(mail.getIsHappy());
    }

    private void sendHappyMail(Mail mail) {
        // Simulate sending happy mail
        logger.info("Sending Happy Mail to recipients: {}", mail.getMailList());
        // Here could be integration with SMTP or external mail service
    }

    private void sendGloomyMail(Mail mail) {
        // Simulate sending gloomy mail
        logger.info("Sending Gloomy Mail to recipients: {}", mail.getMailList());
        // Here could be integration with SMTP or external mail service
    }
}