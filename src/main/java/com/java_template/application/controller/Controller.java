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
import java.util.stream.Collectors;
import java.util.UUID;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/mail")
@Slf4j
@RequiredArgsConstructor
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
            logger.info("Mail entity created with technicalId {}", technicalId.toString());
            // processMail call removed for workflow extraction
            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId.toString());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            logger.error("Illegal argument exception in createMail", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            logger.error("Exception in createMail", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{technicalId}")
    public ResponseEntity<Mail> getMailById(@PathVariable String technicalId) {
        try {
            UUID uuid;
            try {
                uuid = UUID.fromString(technicalId);
            } catch (IllegalArgumentException e) {
                logger.error("Invalid UUID format for technicalId {}", technicalId);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Mail.ENTITY_NAME, ENTITY_VERSION, uuid);
            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                logger.error("Mail entity not found for technicalId {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            // Remove technicalId field from node before mapping to Mail
            node.remove("technicalId");
            Mail mail = node.traverse().readValueAs(Mail.class);
            return ResponseEntity.ok(mail);
        } catch (IllegalArgumentException e) {
            logger.error("Illegal argument exception in getMailById", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            logger.error("Exception in getMailById", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping(params = "isHappy")
    public ResponseEntity<List<Mail>> getMailsByHappiness(@RequestParam Boolean isHappy) {
        try {
            Condition cond = Condition.of("$.isHappy", "EQUALS", isHappy);
            SearchConditionRequest conditionRequest = SearchConditionRequest.group("AND", cond);
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    Mail.ENTITY_NAME, ENTITY_VERSION, conditionRequest, true);
            ArrayNode arrayNode = filteredItemsFuture.get();
            if (arrayNode == null) {
                return ResponseEntity.ok(Collections.emptyList());
            }
            List<Mail> mails = new ArrayList<>();
            for (int i = 0; i < arrayNode.size(); i++) {
                ObjectNode node = (ObjectNode) arrayNode.get(i);
                node.remove("technicalId");
                Mail mail = node.traverse().readValueAs(Mail.class);
                mails.add(mail);
            }
            return ResponseEntity.ok(mails);
        } catch (IllegalArgumentException e) {
            logger.error("Illegal argument exception in getMailsByHappiness", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            logger.error("Exception in getMailsByHappiness", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

// Removed processMail and its private helpers for workflow extraction
}