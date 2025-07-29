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
            if (!mail.isValid()) {
                log.error("Invalid mail entity received");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Mail.ENTITY_NAME,
                    ENTITY_VERSION,
                    mail
            );

            UUID technicalId = idFuture.get();

            log.info("Mail created with technicalId {}", technicalId);

            processMail(technicalId.toString(), mail);

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId.toString());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            log.error("Invalid argument: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error creating mail", e);
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
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Mail.ENTITY_NAME,
                    ENTITY_VERSION,
                    technicalId
            );

            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                log.error("Mail not found for technicalId {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            // Convert ObjectNode to Mail
            Mail mail = node.traverse().readValueAs(Mail.class);
            return ResponseEntity.ok(mail);
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID format: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error retrieving mail", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            log.error("Unexpected error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private void processMail(String technicalId, Mail mail) {
        log.info("Processing mail with technicalId {}", technicalId);

        if (Boolean.TRUE.equals(mail.getIsHappy())) {
            if (checkMailHappy(mail)) {
                sendHappyMail(technicalId, mail);
            } else {
                log.error("Mail failed happy criteria check: {}", technicalId);
            }
        } else {
            if (checkMailGloomy(mail)) {
                sendGloomyMail(technicalId, mail);
            } else {
                log.error("Mail failed gloomy criteria check: {}", technicalId);
            }
        }
    }

    private boolean checkMailHappy(Mail mail) {
        // Criteria: mail isHappy == true
        return Boolean.TRUE.equals(mail.getIsHappy());
    }

    private boolean checkMailGloomy(Mail mail) {
        // Criteria: mail isHappy == false
        return Boolean.FALSE.equals(mail.getIsHappy());
    }

    private void sendHappyMail(String technicalId, Mail mail) {
        // Simulate sending happy mail
        log.info("Sending happy mail to {} with subject '{}'", mail.getMailList(), mail.getSubject());
        // Here could be integration with JavaMailSender or external mail API
        // For prototype, we just log success
        log.info("Happy mail sent successfully for technicalId {}", technicalId);
    }

    private void sendGloomyMail(String technicalId, Mail mail) {
        // Simulate sending gloomy mail
        log.info("Sending gloomy mail to {} with subject '{}'", mail.getMailList(), mail.getSubject());
        // For prototype, we just log success
        log.info("Gloomy mail sent successfully for technicalId {}", technicalId);
    }
}