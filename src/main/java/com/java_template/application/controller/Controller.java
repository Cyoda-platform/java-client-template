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
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    @PostMapping
    public ResponseEntity<Map<String, String>> createMail(@RequestBody Mail mail) {
        try {
            if (mail == null || !mail.isValid()) {
                logger.error("Invalid Mail entity submitted");
                return ResponseEntity.badRequest().build();
            }
            CompletableFuture<UUID> idFuture = entityService.addItem(Mail.ENTITY_NAME, ENTITY_VERSION, mail);
            UUID technicalId = idFuture.get();
            logger.info("Mail entity created with technicalId: {}", technicalId);
            processMail(technicalId.toString(), mail);
            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId.toString());
            return ResponseEntity.status(201).body(response);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument when creating Mail entity", e);
            return ResponseEntity.badRequest().build();
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error creating Mail entity", e);
            return ResponseEntity.status(500).build();
        } catch (Exception e) {
            logger.error("Unexpected error creating Mail entity", e);
            return ResponseEntity.status(500).build();
        }
    }

    @GetMapping("/{technicalId}")
    public ResponseEntity<Mail> getMailById(@PathVariable String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Mail.ENTITY_NAME, ENTITY_VERSION, id);
            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                logger.error("Mail not found for technicalId: {}", technicalId);
                return ResponseEntity.notFound().build();
            }
            Mail mail = node.traverse().readValueAs(Mail.class);
            logger.info("Retrieved Mail entity for technicalId: {}", technicalId);
            return ResponseEntity.ok(mail);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid technicalId format: {}", technicalId, e);
            return ResponseEntity.badRequest().build();
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error retrieving Mail entity by technicalId: {}", technicalId, e);
            return ResponseEntity.status(500).build();
        } catch (Exception e) {
            logger.error("Unexpected error retrieving Mail entity by technicalId: {}", technicalId, e);
            return ResponseEntity.status(500).build();
        }
    }

    @GetMapping
    public ResponseEntity<List<Mail>> getMailsByCriteria(@RequestParam(required = false) Boolean isHappy) {
        try {
            if (isHappy == null) {
                CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(Mail.ENTITY_NAME, ENTITY_VERSION);
                ArrayNode nodes = itemsFuture.get();
                List<Mail> result = new ArrayList<>();
                for (int i = 0; i < nodes.size(); i++) {
                    ObjectNode node = (ObjectNode) nodes.get(i);
                    Mail mail = node.traverse().readValueAs(Mail.class);
                    result.add(mail);
                }
                logger.info("Retrieved {} Mail entities", result.size());
                return ResponseEntity.ok(result);
            } else {
                Condition cond = Condition.of("$.isHappy", "EQUALS", isHappy);
                SearchConditionRequest condition = SearchConditionRequest.group("AND", cond);
                CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(Mail.ENTITY_NAME, ENTITY_VERSION, condition, true);
                ArrayNode nodes = filteredItemsFuture.get();
                List<Mail> result = new ArrayList<>();
                for (int i = 0; i < nodes.size(); i++) {
                    ObjectNode node = (ObjectNode) nodes.get(i);
                    Mail mail = node.traverse().readValueAs(Mail.class);
                    result.add(mail);
                }
                logger.info("Retrieved {} Mail entities by criteria isHappy={}", result.size(), isHappy);
                return ResponseEntity.ok(result);
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error retrieving Mail entities by criteria", e);
            return ResponseEntity.status(500).build();
        } catch (Exception e) {
            logger.error("Unexpected error retrieving Mail entities by criteria", e);
            return ResponseEntity.status(500).build();
        }
    }

    private void processMail(String technicalId, Mail mail) {
        boolean happyCriteriaMet = checkMailHappy(mail);
        boolean gloomyCriteriaMet = checkMailGloomy(mail);

        if (happyCriteriaMet) {
            sendHappyMail(technicalId, mail);
        } else if (gloomyCriteriaMet) {
            sendGloomyMail(technicalId, mail);
        } else {
            logger.error("Mail entity with technicalId {} failed criteria check", technicalId);
        }
    }

    private boolean checkMailHappy(Mail mail) {
        return Boolean.TRUE.equals(mail.getIsHappy());
    }

    private boolean checkMailGloomy(Mail mail) {
        return Boolean.FALSE.equals(mail.getIsHappy());
    }

    private void sendHappyMail(String technicalId, Mail mail) {
        logger.info("Sending happy mail for technicalId: {}, to recipients: {}", technicalId, mail.getMailList());
        // TODO: Implement sending happy mail logic or integrate with external mail service
    }

    private void sendGloomyMail(String technicalId, Mail mail) {
        logger.info("Sending gloomy mail for technicalId: {}, to recipients: {}", technicalId, mail.getMailList());
        // TODO: Implement sending gloomy mail logic or integrate with external mail service
    }
}