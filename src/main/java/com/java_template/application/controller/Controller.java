package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.application.entity.Mail;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/mails")
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class Controller {

    private final EntityService entityService;
    private static final String ENTITY_NAME = "Mail";

    @PostMapping
    public ResponseEntity<?> createMail(@RequestBody Mail mail) {
        try {
            if (mail.getMailList() == null || mail.getMailList().isEmpty()) {
                log.error("Mail list is empty");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "mailList cannot be empty"));
            }
            if (mail.getIsHappy() == null) {
                log.error("isHappy field is null");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "isHappy must be provided"));
            }

            mail.setStatus("PENDING");

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    ENTITY_NAME,
                    ENTITY_VERSION,
                    mail
            );
            UUID technicalId = idFuture.join();
            log.info("Created Mail entity with technicalId: {}", technicalId);

            processMail(technicalId, mail);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId.toString()));
        } catch (IllegalArgumentException ex) {
            log.error("Invalid argument: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            log.error("Error creating mail: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getMailById(@PathVariable String technicalId) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    ENTITY_NAME,
                    ENTITY_VERSION,
                    UUID.fromString(technicalId)
            );
            ObjectNode item = itemFuture.join();
            if (item == null || item.isEmpty()) {
                log.error("Mail with technicalId {} not found", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Mail not found"));
            }
            return ResponseEntity.ok(item);
        } catch (IllegalArgumentException ex) {
            log.error("Invalid technicalId format: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            log.error("Error retrieving mail {}: {}", technicalId, ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", ex.getMessage()));
        }
    }

    private void processMail(UUID technicalId, Mail mail) {
        log.info("Processing Mail with ID: {}", technicalId);

        try {
            if (Boolean.TRUE.equals(mail.getIsHappy())) {
                sendHappyMail(mail);
            } else {
                sendGloomyMail(mail);
            }
            mail.setStatus("SENT");
            log.info("Mail with ID {} sent successfully", technicalId);
        } catch (Exception e) {
            mail.setStatus("FAILED");
            log.error("Failed to send Mail with ID {}: {}", technicalId, e.getMessage());
        }

        // Instead of updating, create a new entity version with reference to previous technicalId
        Mail updatedMail = new Mail();
        updatedMail.setMailList(mail.getMailList());
        updatedMail.setIsHappy(mail.getIsHappy());
        updatedMail.setStatus(mail.getStatus());
        updatedMail.setPreviousTechnicalId(technicalId.toString()); // Assuming this field exists to reference previous

        entityService.addItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                updatedMail
        ).join();
    }

    private void sendHappyMail(Mail mail) {
        log.info("Sending HAPPY mail to recipients: {}", mail.getMailList());
    }

    private void sendGloomyMail(Mail mail) {
        log.info("Sending GLOOMY mail to recipients: {}", mail.getMailList());
    }
}