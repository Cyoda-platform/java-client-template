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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/mail")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private final EntityService entityService;
    private static final String ENTITY_NAME = "mail";
    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    @PostMapping
    public ResponseEntity<?> createMail(@RequestBody Mail mail) {
        try {
            if (!mail.isValid()) {
                logger.error("Invalid Mail entity received");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid mail data"));
            }
            // Add mail entity via EntityService
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    ENTITY_NAME,
                    ENTITY_VERSION,
                    mail
            );

            UUID technicalId = idFuture.join();
            String technicalIdStr = technicalId.toString();
            logger.info("Mail entity created with technicalId: {}", technicalIdStr);

            // processMail method removed during extraction

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalIdStr));
        } catch (IllegalArgumentException e) {
            logger.error("Illegal argument exception: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Failed to create mail: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getMailById(@PathVariable String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    ENTITY_NAME,
                    ENTITY_VERSION,
                    uuid
            );

            ObjectNode node = itemFuture.join();
            if (node == null || node.isEmpty()) {
                logger.error("Mail entity not found for technicalId: {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            // Convert ObjectNode to Mail object
            Mail mail = node.traverse().readValueAs(Mail.class);
            return ResponseEntity.ok(mail);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid technicalId format"));
        } catch (Exception e) {
            logger.error("Failed to retrieve mail: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

}