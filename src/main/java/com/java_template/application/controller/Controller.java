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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.TimeoutException;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/mail")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    // Keep AtomicLong for local id generation to generate some id string for response (optional)
    private final AtomicLong mailIdCounter = new AtomicLong(1);

    @PostMapping
    public ResponseEntity<Map<String, String>> createMail(@RequestBody Mail mail) {
        try {
            if (mail == null || !mail.isValid()) {
                logger.error("Invalid Mail entity received");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            mail.setStatus("PENDING");

            // call entityService.addItem asynchronously
            CompletableFuture<UUID> idFuture = entityService.addItem(Mail.ENTITY_NAME, ENTITY_VERSION, mail);
            UUID technicalId = idFuture.get();

            String technicalIdStr = technicalId.toString();

            logger.info("Mail entity created with technicalId: {}", technicalIdStr);

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalIdStr);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            logger.error("IllegalArgumentException in createMail: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            logger.error("Exception in createMail: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{technicalId}")
    public ResponseEntity<Mail> getMailById(@PathVariable String technicalId) {
        try {
            UUID technicalUUID = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Mail.ENTITY_NAME, ENTITY_VERSION, technicalUUID);
            ObjectNode node = itemFuture.get();

            if (node == null || node.isEmpty()) {
                logger.error("Mail entity not found for technicalId: {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            // Convert ObjectNode to Mail entity
            Mail mail = node.traverse().readValueAs(Mail.class);
            return ResponseEntity.ok(mail);

        } catch (IllegalArgumentException e) {
            logger.error("IllegalArgumentException in getMailById: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            logger.error("Exception in getMailById: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

}