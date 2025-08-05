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
import java.util.UUID;

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
            if (!mail.isValid()) {
                log.error("Validation failed for Mail entity");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Mail.ENTITY_NAME,
                    ENTITY_VERSION,
                    mail
            );
            UUID technicalId = idFuture.get();

            // processMail logic preserved from prototype
            processMail(technicalId.toString(), mail);
            log.info("Mail processed successfully with id {}", technicalId);

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId.toString());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            log.error("Validation or processing error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error creating Mail entity: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            log.error("Unexpected error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Mail> getMailById(@PathVariable("id") String id) {
        try {
            UUID technicalId;
            try {
                technicalId = UUID.fromString(id);
            } catch (IllegalArgumentException ex) {
                log.error("Invalid UUID format for id {}", id);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Mail.ENTITY_NAME,
                    ENTITY_VERSION,
                    technicalId
            );
            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                log.error("Mail entity not found with id {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            // Convert ObjectNode to Mail - assuming Mail has a constructor or method to parse ObjectNode
            // Otherwise, use Jackson ObjectMapper or similar (assuming available)
            Mail mail = convertObjectNodeToMail(node);
            if (mail == null) {
                log.error("Failed to convert data to Mail entity for id {}", id);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }

            return ResponseEntity.ok(mail);
        } catch (IllegalArgumentException e) {
            log.error("Illegal argument: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error retrieving Mail entity: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            log.error("Unexpected error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private Mail convertObjectNodeToMail(ObjectNode node) {
        try {
            // Assuming Mail class has a static method fromJson or similar or use ObjectMapper
            // Here we use Jackson ObjectMapper assuming it's available via Spring Boot context
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.treeToValue(node, Mail.class);
        } catch (Exception e) {
            log.error("Error converting ObjectNode to Mail: {}", e.getMessage(), e);
            return null;
        }
    }

    private void processMail(String technicalId, Mail mail) {
        // Validation: check mailList not empty and content not blank
        if (mail.getMailList() == null || mail.getMailList().isEmpty()) {
            log.error("Mail list is empty for mail id {}", technicalId);
            throw new IllegalArgumentException("mailList cannot be empty");
        }
        if (mail.getContent() == null || mail.getContent().isBlank()) {
            log.error("Mail content is blank for mail id {}", technicalId);
            throw new IllegalArgumentException("content cannot be blank");
        }
        // Processing: send happy or gloomy mail based on isHappy flag
        if (mail.isHappy()) {
            sendHappyMail(technicalId, mail);
        } else {
            sendGloomyMail(technicalId, mail);
        }
        // Completion: persist sending status if needed (simulate here)
        log.info("Mail with id {} sent successfully. isHappy={}", technicalId, mail.isHappy());
    }

    private void sendHappyMail(String technicalId, Mail mail) {
        for (String recipient : mail.getMailList()) {
            // Simulate sending happy mail
            log.info("Sending HAPPY mail to {}: {}", recipient, mail.getContent());
        }
    }

    private void sendGloomyMail(String technicalId, Mail mail) {
        for (String recipient : mail.getMailList()) {
            // Simulate sending gloomy mail
            log.info("Sending GLOOMY mail to {}: {}", recipient, mail.getContent());
        }
    }
}