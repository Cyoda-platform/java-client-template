package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
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
@RequestMapping(path = "/mail")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private final EntityService entityService;

    @PostMapping
    public ResponseEntity<Map<String, String>> createMail(@RequestBody Mail mail) {
        try {
            if (!mail.isValid()) {
                log.error("Invalid Mail entity received");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            CompletableFuture<UUID> idFuture = entityService.addItem(Mail.ENTITY_NAME, ENTITY_VERSION, mail);
            UUID technicalId = idFuture.get();
            log.info("Mail entity created with technicalId: {}", technicalId);
            processMail(technicalId.toString(), mail);
            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId.toString());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            log.error("Illegal argument exception in createMail", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Exception in createMail", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            log.error("Unexpected exception in createMail", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{technicalId}")
    public ResponseEntity<Mail> getMailById(@PathVariable String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Mail.ENTITY_NAME, ENTITY_VERSION, uuid);
            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                log.error("Mail entity not found for technicalId: {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Mail mail = node.traverse().readValueAs(Mail.class);
            log.info("Mail entity retrieved for technicalId: {}", technicalId);
            return ResponseEntity.ok(mail);
        } catch (IllegalArgumentException e) {
            log.error("Illegal argument exception in getMailById", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Exception in getMailById", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            log.error("Unexpected exception in getMailById", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private void processMail(String technicalId, Mail mail) {
        if (mail.getMailList() == null || mail.getMailList().isEmpty()) {
            log.error("MailList is empty for mail with technicalId: {}", technicalId);
            return;
        }
        if (mail.getContent() == null || mail.getContent().isBlank()) {
            log.error("Mail content is blank for mail with technicalId: {}", technicalId);
            return;
        }
        if (Boolean.TRUE.equals(mail.getIsHappy())) {
            processMailSendHappy(technicalId, mail);
        } else {
            processMailSendGloomy(technicalId, mail);
        }
    }

    private void processMailSendHappy(String technicalId, Mail mail) {
        for (String recipient : mail.getMailList()) {
            log.info("Sending HAPPY mail to {}: {}", recipient, mail.getContent());
            // Implement actual mail sending logic here if needed
        }
        log.info("All happy mails sent successfully for mail with technicalId: {}", technicalId);
    }

    private void processMailSendGloomy(String technicalId, Mail mail) {
        for (String recipient : mail.getMailList()) {
            log.info("Sending GLOOMY mail to {}: {}", recipient, mail.getContent());
            // Implement actual mail sending logic here if needed
        }
        log.info("All gloomy mails sent successfully for mail with technicalId: {}", technicalId);
    }
}