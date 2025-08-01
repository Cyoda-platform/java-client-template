package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Mail;
import com.java_template.common.service.EntityService;
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

            CompletableFuture<UUID> idFuture = entityService.addItem(Mail.ENTITY_NAME, ENTITY_VERSION, mail);
            UUID technicalId = idFuture.get(); // wait for completion

            log.info("Mail entity created with technicalId: {}", technicalId);

            processMail(technicalId.toString(), mail);

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId.toString());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            log.error("Invalid argument: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error creating Mail entity", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            log.error("Unexpected error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Mail> getMailById(@PathVariable("id") String id) {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Mail.ENTITY_NAME, ENTITY_VERSION, technicalId);
            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                log.error("Mail entity not found for technicalId: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            // Convert ObjectNode to Mail
            Mail mail = entityService.getObjectMapper().treeToValue(node, Mail.class);
            return ResponseEntity.ok(mail);

        } catch (IllegalArgumentException e) {
            log.error("Invalid argument: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error retrieving Mail entity", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            log.error("Unexpected error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private void processMail(String technicalId, Mail mail) {
        // Validation: mail.isValid() already checked in controller
        log.info("Processing Mail entity with technicalId: {}", technicalId);

        // Check criteria and call processors
        if (mail.isHappy()) {
            processMailSendHappyMail(technicalId, mail);
        } else {
            processMailSendGloomyMail(technicalId, mail);
        }
    }

    private void processMailSendHappyMail(String technicalId, Mail mail) {
        log.info("Sending Happy Mail for technicalId: {}", technicalId);
        for (String recipient : mail.getMailList()) {
            // Simulate sending happy mail
            log.info("Happy mail sent to: {} with subject: {}", recipient, mail.getSubject());
        }
        log.info("Completed sending Happy Mail for technicalId: {}", technicalId);
    }

    private void processMailSendGloomyMail(String technicalId, Mail mail) {
        log.info("Sending Gloomy Mail for technicalId: {}", technicalId);
        for (String recipient : mail.getMailList()) {
            // Simulate sending gloomy mail
            log.info("Gloomy mail sent to: {} with subject: {}", recipient, mail.getSubject());
        }
        log.info("Completed sending Gloomy Mail for technicalId: {}", technicalId);
    }
}