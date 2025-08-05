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
            processMail(technicalId.toString(), mail);
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

    private void processMail(String technicalId, Mail mail) {
        try {
            if (mail.getMailList() == null || mail.getMailList().isEmpty()) {
                logger.error("Mail with technicalId {} has empty mailList", technicalId);
                return;
            }
            if (mail.getContent() == null || mail.getContent().isBlank()) {
                logger.error("Mail with technicalId {} has empty content", technicalId);
                return;
            }

            if (Boolean.TRUE.equals(mail.getIsHappy())) {
                sendHappyMail(technicalId, mail);
            } else if (Boolean.FALSE.equals(mail.getIsHappy())) {
                sendGloomyMail(technicalId, mail);
            } else {
                logger.info("Mail with technicalId {} has undefined isHappy status", technicalId);
            }
        } catch (Exception e) {
            logger.error("Exception in processMail for technicalId {}", technicalId, e);
        }
    }

    private void sendHappyMail(String technicalId, Mail mail) {
        for (String recipient : mail.getMailList()) {
            logger.info("Sending HAPPY mail to {}: {}", recipient, mail.getContent());
            // Real mail sending logic with JavaMail or external API would go here
        }
        logger.info("Completed sending happy mail for technicalId {}", technicalId);
    }

    private void sendGloomyMail(String technicalId, Mail mail) {
        for (String recipient : mail.getMailList()) {
            logger.info("Sending GLOOMY mail to {}: {}", recipient, mail.getContent());
            // Real mail sending logic with JavaMail or external API would go here
        }
        logger.info("Completed sending gloomy mail for technicalId {}", technicalId);
    }
}