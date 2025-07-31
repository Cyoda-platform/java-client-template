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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.UUID;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/mail")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    @PostMapping
    public ResponseEntity<Map<String, String>> createMail(@RequestBody Mail mail) {
        try {
            if (!mail.isValid()) {
                logger.error("Invalid Mail entity received");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            // Add item to EntityService
            CompletableFuture<UUID> idFuture = entityService.addItem(Mail.ENTITY_NAME, ENTITY_VERSION, mail);
            UUID technicalId = idFuture.get(5, TimeUnit.SECONDS);
            logger.info("Mail entity created with technicalId: {}", technicalId);

            try {
                // processMail call removed
            } catch (Exception e) {
                logger.error("Error processing Mail entity with technicalId: {}", technicalId, e);
                // In a production system, consider retry or dead-letter queue here
            }

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId.toString());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException iae) {
            logger.error("Illegal argument exception on createMail", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            logger.error("Exception on createMail", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Mail> getMailById(@PathVariable("id") String id) {
        try {
            UUID technicalId;
            try {
                technicalId = UUID.fromString(id);
            } catch (IllegalArgumentException e) {
                logger.error("Invalid UUID format for technicalId: {}", id);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Mail.ENTITY_NAME, ENTITY_VERSION, technicalId);
            ObjectNode node = itemFuture.get(5, TimeUnit.SECONDS);
            if (node == null || node.isEmpty()) {
                logger.error("Mail entity not found for technicalId: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            // Convert ObjectNode to Mail entity
            Mail mail = convertObjectNodeToMail(node);
            logger.info("Retrieved Mail entity with technicalId: {}", id);
            return ResponseEntity.ok(mail);

        } catch (IllegalArgumentException iae) {
            logger.error("Illegal argument exception on getMailById", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            logger.error("Exception on getMailById", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Utility method to convert ObjectNode to Mail entity
    private Mail convertObjectNodeToMail(ObjectNode node) {
        try {
            // Assuming Jackson ObjectMapper is available via static instance or can be autowired
            // Here we'll create a new ObjectMapper instance for simplicity
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            // Remove technicalId field before conversion if exists
            node.remove("technicalId");
            return mapper.treeToValue(node, Mail.class);
        } catch (Exception e) {
            logger.error("Error converting ObjectNode to Mail", e);
            return null;
        }
    }
}
