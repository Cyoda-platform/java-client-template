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
            if (mail == null || !mail.isValid()) {
                log.error("Invalid Mail entity received");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            CompletableFuture<UUID> idFuture = entityService.addItem(Mail.ENTITY_NAME, ENTITY_VERSION, mail);
            UUID technicalId = idFuture.get(); // blocking get since method is synchronous

            log.info("Mail created with technicalId={}", technicalId);

            processMail(technicalId.toString(), mail);

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId.toString());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            log.error("IllegalArgumentException in createMail", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (InterruptedException | ExecutionException e) {
            log.error("ExecutionException in createMail", e);
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            log.error("Exception in createMail", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{technicalId}")
    public ResponseEntity<Mail> getMailById(@PathVariable String technicalId) {
        try {
            UUID techIdUuid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Mail.ENTITY_NAME, ENTITY_VERSION, techIdUuid);
            ObjectNode node = itemFuture.get(); // blocking get

            if (node == null) {
                log.error("Mail not found for technicalId={}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            Mail mail = node.traverse().readValueAs(Mail.class);
            log.info("Mail retrieved for technicalId={}", technicalId);
            return ResponseEntity.ok(mail);
        } catch (IllegalArgumentException e) {
            log.error("IllegalArgumentException in getMailById", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (InterruptedException | ExecutionException e) {
            log.error("ExecutionException in getMailById", e);
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            log.error("Exception in getMailById", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private void processMail(String technicalId, Mail mail) {
        try {
            log.info("Processing Mail with technicalId={}", technicalId);

            if (mail.getIsHappy()) {
                sendHappyMail(technicalId, mail);
            } else {
                sendGloomyMail(technicalId, mail);
            }
        } catch (Exception e) {
            log.error("Exception in processMail for technicalId={}", technicalId, e);
        }
    }

    private void sendHappyMail(String technicalId, Mail mail) {
        log.info("Sending Happy Mail to recipients: {}", mail.getMailList());
        // Simulate sending mails
    }

    private void sendGloomyMail(String technicalId, Mail mail) {
        log.info("Sending Gloomy Mail to recipients: {}", mail.getMailList());
        // Simulate sending mails
    }
}