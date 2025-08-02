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

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/mails")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    @PostMapping
    public ResponseEntity<Map<String, String>> createMail(@RequestBody Mail mail) {
        try {
            if (mail == null || !mail.isValid()) {
                logger.error("Invalid Mail entity received.");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            CompletableFuture<UUID> idFuture = entityService.addItem(Mail.ENTITY_NAME, ENTITY_VERSION, mail);
            UUID technicalId = idFuture.get();

            logger.info("Mail entity created with technicalId: {}", technicalId);
            processMail(technicalId, mail);

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId.toString());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument while creating mail", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            logger.error("Error occurred while creating mail", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{technicalId}")
    public ResponseEntity<Mail> getMailById(@PathVariable String technicalId) {
        try {
            UUID uuid;
            try {
                uuid = UUID.fromString(technicalId);
            } catch (IllegalArgumentException e) {
                logger.error("Invalid UUID format for technicalId: {}", technicalId);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Mail.ENTITY_NAME, ENTITY_VERSION, uuid);
            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                logger.error("Mail entity not found for technicalId: {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Mail mail = node.traverse().readValueAs(Mail.class);
            logger.info("Mail entity retrieved for technicalId: {}", technicalId);
            return ResponseEntity.ok(mail);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument while retrieving mail", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            logger.error("Error occurred while retrieving mail", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private void processMail(UUID technicalId, Mail mail) {
        logger.info("Processing Mail entity with technicalId: {}", technicalId);

        // Validate criteria if needed - here assumed always true for simplicity
        boolean happyCriteria = checkMailHappyCriteria(mail);
        boolean gloomyCriteria = checkMailGloomyCriteria(mail);

        try {
            if (happyCriteria && mail.isHappy()) {
                sendHappyMail(mail);
                logger.info("Happy mail sent for technicalId: {}", technicalId);
            } else if (gloomyCriteria && !mail.isHappy()) {
                sendGloomyMail(mail);
                logger.info("Gloomy mail sent for technicalId: {}", technicalId);
            } else {
                logger.info("Mail with technicalId {} does not meet any sending criteria", technicalId);
            }
        } catch (Exception e) {
            logger.error("Failed to send mail for technicalId: {}", technicalId, e);
        }
    }

    private boolean checkMailHappyCriteria(Mail mail) {
        // Implement actual happy mail criteria logic here
        // For prototype, assume if isHappy is true, criteria is met
        return mail.isHappy();
    }

    private boolean checkMailGloomyCriteria(Mail mail) {
        // Implement actual gloomy mail criteria logic here
        // For prototype, assume if isHappy is false, criteria is met
        return !mail.isHappy();
    }

    private void sendHappyMail(Mail mail) {
        // Implement sending happy mail logic here
        // For prototype, simulate sending by logging
        logger.info("Sending happy mail to recipients: {}", mail.getMailList());
    }

    private void sendGloomyMail(Mail mail) {
        // Implement sending gloomy mail logic here
        // For prototype, simulate sending by logging
        logger.info("Sending gloomy mail to recipients: {}", mail.getMailList());
    }
}