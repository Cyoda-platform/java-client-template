package com.java_template.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping(path = "/mail")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @PostMapping
    public ResponseEntity<?> createMail(@RequestBody Mail mail) {
        try {
            if (mail == null) {
                logger.error("Received null mail in createMail");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Mail cannot be null"));
            }
            if (mail.getMailList() == null || mail.getMailList().isEmpty()) {
                logger.error("Mail list is empty or null");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "mailList must contain at least one email address"));
            }
            for (String email : mail.getMailList()) {
                if (email == null || email.isBlank()) {
                    logger.error("Invalid email in mailList: '{}'", email);
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(Map.of("error", "mailList contains blank or null email"));
                }
            }

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    "mail",
                    ENTITY_VERSION,
                    mail
            );

            UUID technicalId = idFuture.join();

            mail.setId(technicalId.toString());

            logger.info("Mail entity created with ID: {}", technicalId);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("technicalId", technicalId.toString()));

        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument in createMail", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Exception in createMail", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getMailById(@PathVariable("id") String id) throws JsonProcessingException {
        try {
            UUID technicalId;
            try {
                technicalId = UUID.fromString(id);
            } catch (IllegalArgumentException e) {
                logger.error("Invalid UUID format for id: {}", id);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Invalid technicalId format"));
            }

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    "mail",
                    ENTITY_VERSION,
                    technicalId
            );

            ObjectNode node = itemFuture.join();

            if (node == null || node.isEmpty()) {
                logger.error("Mail not found with ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Mail not found for technicalId: " + id));
            }

            Mail mail = objectMapper.treeToValue(node, Mail.class);

            return ResponseEntity.ok(mail);

        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument in getMailById", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (JsonProcessingException e) {
            logger.error("Failed to deserialize mail entity", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to parse mail entity"));
        } catch (Exception e) {
            logger.error("Exception in getMailById", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error"));
        }
    }
}