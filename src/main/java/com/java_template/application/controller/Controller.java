package com.java_template.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Mail;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping(path = "/mail")
public class Controller {

    private static final String ENTITY_NAME = "Mail";

    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<?> createMail(@Valid @RequestBody Mail mail) throws JsonProcessingException {
        try {
            // Validate input fields required for creation
            if (mail.getMailList() == null || mail.getMailList().isEmpty()) {
                logger.error("Mail creation failed: mailList is empty");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "mailList must not be empty"));
            }
            for (String email : mail.getMailList()) {
                if (email == null || email.isBlank()) {
                    logger.error("Mail creation failed: mailList contains blank email");
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "mailList contains invalid email"));
                }
            }
            if (mail.getContent() == null || mail.getContent().isBlank()) {
                logger.error("Mail creation failed: content is blank");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "content must not be blank"));
            }

            mail.setStatus("PENDING"); // initial status

            // Call entityService to add item
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    ENTITY_NAME,
                    ENTITY_VERSION,
                    mail
            );
            UUID technicalId = idFuture.get();

            logger.info("Mail created with technicalId {}", technicalId);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId.toString()));
        } catch (IllegalArgumentException e) {
            logger.error("Illegal argument during mail creation: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (ExecutionException ee) {
            logger.error("Execution exception during mail creation: {}", ee.getMessage(), ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted during mail creation: {}", ie.getMessage(), ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        } catch (Exception e) {
            logger.error("Failed to create mail: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getMailById(@PathVariable String technicalId) throws JsonProcessingException {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    ENTITY_NAME,
                    ENTITY_VERSION,
                    uuid
            );
            ObjectNode node = itemFuture.get();
            if (node == null) {
                logger.error("Mail not found with technicalId {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Mail not found"));
            }

            // Convert ObjectNode to Mail entity
            Mail mail = objectMapper.treeToValue(node, Mail.class);

            // Map Mail fields plus technicalId for response
            Map<String, Object> response = new HashMap<>();
            response.put("technicalId", technicalId);
            response.put("isHappy", mail.getIsHappy());
            response.put("mailList", mail.getMailList());
            response.put("content", mail.getContent());
            response.put("status", mail.getStatus());

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid technicalId format: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid technicalId format"));
        } catch (ExecutionException ee) {
            if (ee.getCause() instanceof NoSuchElementException) {
                logger.error("Mail not found with technicalId {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Mail not found"));
            }
            logger.error("Execution exception: {}", ee.getMessage(), ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrupted during getMailById: {}", ie.getMessage(), ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        } catch (Exception e) {
            logger.error("Failed to get mail by id: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

}