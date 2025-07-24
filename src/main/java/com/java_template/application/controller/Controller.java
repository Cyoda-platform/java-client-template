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

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/mail")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private final EntityService entityService;

    @PostMapping
    public ResponseEntity<Map<String, String>> createMail(@RequestBody Mail mail) {
        try {
            if (mail.getMailList() == null || mail.getMailList().isEmpty()) {
                log.error("Mail list is empty");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "mailList must not be empty"));
            }
            if (mail.getIsHappy() == null) {
                log.error("isHappy flag is null");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "isHappy must be set"));
            }

            mail.setStatus("PENDING");

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    "Mail",
                    ENTITY_VERSION,
                    mail
            );

            UUID technicalId = idFuture.join();

            processMail(technicalId, mail);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId.toString()));

        } catch (IllegalArgumentException e) {
            log.error("IllegalArgumentException in createMail: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Exception in createMail: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{technicalId}")
    public ResponseEntity<Object> getMailById(@PathVariable String technicalId) {
        try {
            ObjectNode item = entityService.getItem(
                    "Mail",
                    ENTITY_VERSION,
                    UUID.fromString(technicalId)
            ).join();

            if (item == null || item.isEmpty()) {
                log.error("Mail with technicalId {} not found", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Mail not found"));
            }

            return ResponseEntity.ok(item);

        } catch (IllegalArgumentException e) {
            log.error("IllegalArgumentException in getMailById: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Exception in getMailById: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    private void processMail(UUID technicalId, Mail mail) {
        log.info("Processing Mail with technicalId: {}", technicalId);

        if (mail.getMailList() == null || mail.getMailList().isEmpty() || mail.getIsHappy() == null) {
            log.error("Invalid mail data for technicalId: {}", technicalId);
            mail.setStatus("FAILED");
            addUpdatedMailEntity(technicalId, mail);
            return;
        }

        try {
            if (mail.getIsHappy()) {
                sendHappyMail(mail);
            } else {
                sendGloomyMail(mail);
            }
            mail.setStatus("SENT");
            log.info("Mail with technicalId {} sent successfully", technicalId);
        } catch (Exception e) {
            log.error("Failed to send mail with technicalId {}: {}", technicalId, e.getMessage());
            mail.setStatus("FAILED");
        }

        addUpdatedMailEntity(technicalId, mail);
    }

    private void addUpdatedMailEntity(UUID previousTechnicalId, Mail mail) {
        try {
            // Add reference to previous entity
            mail.setPreviousTechnicalId(previousTechnicalId.toString());

            CompletableFuture<UUID> updateFuture = entityService.addItem(
                    "Mail",
                    ENTITY_VERSION,
                    mail
            );

            updateFuture.join();

        } catch (Exception e) {
            log.error("Failed to add updated Mail entity for previousTechnicalId {}: {}", previousTechnicalId, e.getMessage());
        }
    }

    private void sendHappyMail(Mail mail) {
        log.info("sendHappyMail processor invoked for mailList: {}", mail.getMailList());
        // Simulate sending happy mail - real implementation would send emails here
    }

    private void sendGloomyMail(Mail mail) {
        log.info("sendGloomyMail processor invoked for mailList: {}", mail.getMailList());
        // Simulate sending gloomy mail - real implementation would send emails here
    }
}