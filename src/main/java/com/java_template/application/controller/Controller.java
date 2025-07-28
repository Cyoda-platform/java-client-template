package com.java_template.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

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
    private final ObjectMapper objectMapper;
    private static final String ENTITY_NAME = "Mail";

    @PostMapping
    public ResponseEntity<?> createMail(@Valid @RequestBody Mail mail) throws ExecutionException, InterruptedException, JsonProcessingException {
        try {
            if (mail == null) {
                logger.error("Received null Mail object");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Mail object is required"));
            }
            if (!mail.isValid()) {
                logger.error("Invalid Mail entity: missing required fields");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid Mail entity: mailList, content, and isHappy are required"));
            }

            mail.setStatus("CREATED");
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    ENTITY_NAME,
                    ENTITY_VERSION,
                    mail
            );
            UUID technicalId = idFuture.get();

            try {
                // processMail method removed - extracted to workflow prototype
                logger.info("Successfully processed Mail with ID {}", technicalId);
            } catch (Exception e) {
                logger.error("Failed to process Mail with ID {}: {}", technicalId, e.getMessage());
                mail.setStatus("FAILED");
                // TODO: consider update operation if supported
            }

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId.toString()));
        } catch (IllegalArgumentException e) {
            logger.error("IllegalArgumentException in createMail: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error in createMail: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getMailById(@PathVariable @NotBlank String technicalId) throws ExecutionException, InterruptedException, JsonProcessingException {
        try {
            UUID uuid;
            try {
                uuid = UUID.fromString(technicalId);
            } catch (IllegalArgumentException e) {
                logger.error("Invalid UUID format: {}", technicalId);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid technicalId format"));
            }

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(ENTITY_NAME, ENTITY_VERSION, uuid);
            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                logger.error("Mail with ID {} not found", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Mail not found"));
            }

            Mail mail = objectMapper.treeToValue(node, Mail.class);
            return ResponseEntity.ok(mail);
        } catch (IllegalArgumentException e) {
            logger.error("IllegalArgumentException in getMailById: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error in getMailById: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping
    public ResponseEntity<?> getMailByCondition(@RequestParam(required = false) Boolean isHappy) throws ExecutionException, InterruptedException, JsonProcessingException {
        try {
            if (isHappy == null) {
                CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(ENTITY_NAME, ENTITY_VERSION);
                ArrayNode arrayNode = itemsFuture.get();
                List<Mail> mails = new ArrayList<>();
                for (var node : arrayNode) {
                    Mail mail = objectMapper.treeToValue(node, Mail.class);
                    mails.add(mail);
                }
                return ResponseEntity.ok(mails);
            } else {
                Condition condition = Condition.of("$.isHappy", "EQUALS", isHappy);
                SearchConditionRequest condReq = SearchConditionRequest.group("AND", condition);
                CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, condReq, true);
                ArrayNode arrayNode = filteredItemsFuture.get();
                List<Mail> mails = new ArrayList<>();
                for (var node : arrayNode) {
                    Mail mail = objectMapper.treeToValue(node, Mail.class);
                    mails.add(mail);
                }
                return ResponseEntity.ok(mails);
            }
        } catch (IllegalArgumentException e) {
            logger.error("IllegalArgumentException in getMailByCondition: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error in getMailByCondition: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }
}