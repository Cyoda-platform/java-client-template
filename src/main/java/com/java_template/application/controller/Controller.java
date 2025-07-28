package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Mail;
import com.java_template.common.service.EntityService;
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
            if (mail == null || !mail.isValid()) {
                logger.error("Invalid Mail entity received");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            CompletableFuture<UUID> idFuture = entityService.addItem(Mail.ENTITY_NAME, ENTITY_VERSION, mail);
            UUID technicalId = idFuture.get();

            logger.info("Mail entity created with technicalId: {}", technicalId);

            try {
                // processMail method removed
            } catch (Exception e) {
                logger.error("Error processing Mail entity with technicalId: {}, error: {}", technicalId, e.getMessage());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId.toString());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument when creating Mail: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error creating Mail: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error creating Mail: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{technicalId}")
    public ResponseEntity<Mail> getMail(@PathVariable String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Mail.ENTITY_NAME, ENTITY_VERSION, uuid);
            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                logger.error("Mail entity not found for technicalId: {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Mail mail = node.traverse().readValueAs(Mail.class);
            logger.info("Retrieved Mail entity with technicalId: {}", technicalId);
            return ResponseEntity.ok(mail);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format for technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error retrieving Mail entity for technicalId {}: {}", technicalId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error retrieving Mail entity for technicalId {}: {}", technicalId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // processMail and its helper methods removed from Controller.java
}