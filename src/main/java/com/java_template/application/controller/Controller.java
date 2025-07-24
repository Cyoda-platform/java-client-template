package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Mail;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/mail")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private static final String ENTITY_NAME = "Mail";

    private final EntityService entityService;

    @PostMapping
    public ResponseEntity<Map<String, String>> createMail(@RequestBody Mail mail) {
        log.info("Received request to create Mail");
        if (mail == null) {
            log.error("Mail entity is null");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Mail entity is required"));
        }
        if (!mail.isValid()) {
            log.error("Mail entity validation failed");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid Mail entity fields"));
        }

        mail.setStatus("PENDING");

        try {
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    ENTITY_NAME,
                    ENTITY_VERSION,
                    mail
            );
            UUID technicalId = idFuture.get();

            // processMail removed

            log.info("Mail created with technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId.toString()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while creating Mail: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal error"));
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                log.error("Illegal argument error: {}", cause.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", cause.getMessage()));
            }
            log.error("Error creating Mail: {}", cause.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal error"));
        } catch (Exception e) {
            log.error("Error creating Mail: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal error"));
        }
    }

    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getMailById(@PathVariable String technicalId) {
        log.info("Received request to get Mail with technicalId: {}", technicalId);

        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    ENTITY_NAME,
                    ENTITY_VERSION,
                    id
            );
            ObjectNode entityNode = itemFuture.get();
            if (entityNode == null || entityNode.isEmpty()) {
                log.error("Mail with technicalId {} not found", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Mail not found"));
            }
            Mail mail = entityNode.traverse().readValueAs(Mail.class);
            return ResponseEntity.ok(mail);
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID format: {}", technicalId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid technicalId format"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while getting Mail: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal error"));
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                log.error("Illegal argument error: {}", cause.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", cause.getMessage()));
            }
            log.error("Error getting Mail: {}", cause.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal error"));
        } catch (Exception e) {
            log.error("Error getting Mail: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal error"));
        }
    }

    @GetMapping(params = "isHappy")
    public ResponseEntity<?> getMailsByIsHappy(@RequestParam boolean isHappy) {
        log.info("Received request to get Mails filtered by isHappy={}", isHappy);

        try {
            Condition condition = Condition.of("$.isHappy", "EQUALS", isHappy);
            SearchConditionRequest conditionRequest = SearchConditionRequest.group("AND", condition);

            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    ENTITY_NAME,
                    ENTITY_VERSION,
                    conditionRequest,
                    true
            );

            ArrayNode filteredNodes = filteredItemsFuture.get();
            List<Mail> mails = new ArrayList<>();
            filteredNodes.forEach(node -> {
                try {
                    Mail mail = node.traverse().readValueAs(Mail.class);
                    mails.add(mail);
                } catch (Exception ex) {
                    log.error("Error deserializing Mail: {}", ex.getMessage());
                }
            });

            return ResponseEntity.ok(mails);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while getting Mails: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal error"));
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                log.error("Illegal argument error: {}", cause.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", cause.getMessage()));
            }
            log.error("Error getting Mails: {}", cause.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal error"));
        } catch (Exception e) {
            log.error("Error getting Mails: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal error"));
        }
    }

}