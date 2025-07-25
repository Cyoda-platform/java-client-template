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

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/mail")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    // POST /mail - create Mail entity, trigger processing, return technicalId
    @PostMapping
    public ResponseEntity<?> createMail(@RequestBody Mail mail) {
        try {
            // Validate required fields
            if (mail.getIsHappy() == null) {
                logger.error("Validation failed: isHappy is required");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Field 'isHappy' is required"));
            }
            if (mail.getMailList() == null || mail.getMailList().isEmpty()) {
                logger.error("Validation failed: mailList is required and cannot be empty");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Field 'mailList' is required and cannot be empty"));
            }
            for (String email : mail.getMailList()) {
                if (email == null || email.isBlank()) {
                    logger.error("Validation failed: mailList contains blank email");
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "mailList cannot contain blank email addresses"));
                }
            }

            CompletableFuture<UUID> idFuture = entityService.addItem("mail", ENTITY_VERSION, mail);
            UUID technicalId = idFuture.join();

            logger.info("Mail entity created with technicalId: {}", technicalId);

            // Trigger processing
            // processMail(mail); // removed process method call as processMail is extracted

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId.toString()));
        } catch (IllegalArgumentException e) {
            logger.error("Validation error creating Mail entity", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error creating Mail entity", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // GET /mail/{id} - retrieve Mail entity by technicalId
    @GetMapping("/{id}")
    public ResponseEntity<?> getMailById(@PathVariable("id") String id) {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem("mail", ENTITY_VERSION, technicalId);
            ObjectNode node = itemFuture.join();
            if (node == null) {
                logger.error("Mail entity not found for id: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Mail entity not found"));
            }
            logger.info("Retrieved Mail entity for id: {}", id);
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format for id: {}", id, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid id format"));
        } catch (Exception e) {
            logger.error("Error retrieving Mail entity for id: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

}