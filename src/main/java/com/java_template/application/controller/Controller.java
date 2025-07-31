package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Mail;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final EntityService entityService;

    @PostMapping
    public ResponseEntity<Map<String, String>> createMail(@RequestBody Mail mail) {
        try {
            if (mail == null || !mail.isValid()) {
                log.error("Invalid Mail entity received");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            // Use entityService to add item
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Mail.ENTITY_NAME,
                    ENTITY_VERSION,
                    mail
            );
            UUID technicalId = idFuture.get();

            log.info("Mail entity created with technicalId={}", technicalId);

            // Trigger processing
            processMail(technicalId.toString(), mail);

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId.toString());

            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            log.error("IllegalArgumentException in createMail: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Exception in createMail: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            log.error("Unexpected exception in createMail: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{technicalId}")
    public ResponseEntity<Mail> getMail(@PathVariable String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Mail.ENTITY_NAME,
                    ENTITY_VERSION,
                    uuid
            );
            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                log.error("Mail entity not found for technicalId={}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            // Convert ObjectNode to Mail
            Mail mail = new Mail();
            mail = mail.fromObjectNode(node);
            log.info("Mail entity retrieved for technicalId={}", technicalId);
            return ResponseEntity.ok(mail);
        } catch (IllegalArgumentException e) {
            log.error("IllegalArgumentException in getMail: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Exception in getMail: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            log.error("Unexpected exception in getMail: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Business logic for processing Mail entity according to requirements
    private void processMail(String technicalId, Mail mail) {
        log.info("Processing Mail entity technicalId={}", technicalId);

        // Step 1: Validate mailList is non-empty and email format basic check
        List<String> mailList = mail.getMailList();
        if (mailList == null || mailList.isEmpty()) {
            log.error("Mail list empty or null for technicalId={}", technicalId);
            return;
        }
        boolean invalidEmailFound = mailList.stream().anyMatch(email -> email == null || email.isBlank() || !email.contains("@"));
        if (invalidEmailFound) {
            log.error("Invalid email address found in mailList for technicalId={}", technicalId);
            return;
        }

        // Step 2: Determine mail type and send appropriate mails
        if (Boolean.TRUE.equals(mail.getIsHappy())) {
            sendHappyMail(mailList, technicalId);
        } else {
            sendGloomyMail(mailList, technicalId);
        }

        // Step 3: Log completion
        log.info("Finished processing Mail entity technicalId={}", technicalId);
    }

    private void sendHappyMail(List<String> recipients, String technicalId) {
        // Simulate sending happy mail
        for (String recipient : recipients) {
            log.info("Sent HAPPY mail to {} for technicalId={}", recipient, technicalId);
        }
    }

    private void sendGloomyMail(List<String> recipients, String technicalId) {
        // Simulate sending gloomy mail
        for (String recipient : recipients) {
            log.info("Sent GLOOMY mail to {} for technicalId={}", recipient, technicalId);
        }
    }
}