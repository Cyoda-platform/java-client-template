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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/mails")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    @PostMapping
    public ResponseEntity<Map<String, String>> createMail(@RequestBody Mail mail) {
        try {
            if (mail == null || !mail.isValid()) {
                logger.error("Invalid mail entity received");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            mail.setStatus("PENDING");
            CompletableFuture<UUID> idFuture = entityService.addItem(Mail.ENTITY_NAME, ENTITY_VERSION, mail);
            UUID technicalId = idFuture.get(10, TimeUnit.SECONDS);

            logger.info("Mail created with technicalId {}", technicalId);

            processMail(technicalId, mail);

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId.toString());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            logger.error("Illegal argument: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            logger.error("Error creating mail: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{technicalId}")
    public ResponseEntity<Mail> getMailById(@PathVariable String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Mail.ENTITY_NAME, ENTITY_VERSION, id);
            ObjectNode node = itemFuture.get(10, TimeUnit.SECONDS);
            if (node == null || node.isEmpty()) {
                logger.error("Mail not found with technicalId {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Mail mail = convertNodeToMail(node);
            return ResponseEntity.ok(mail);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid technicalId format: {}", technicalId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            logger.error("Error retrieving mail: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping
    public ResponseEntity<List<Mail>> getMailsByIsHappy(@RequestParam Optional<Boolean> isHappy) {
        try {
            if (isHappy.isEmpty()) {
                CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(Mail.ENTITY_NAME, ENTITY_VERSION);
                ArrayNode nodes = itemsFuture.get(10, TimeUnit.SECONDS);
                List<Mail> mails = convertNodesToMails(nodes);
                return ResponseEntity.ok(mails);
            } else {
                SearchConditionRequest condition = SearchConditionRequest.group("AND",
                        Condition.of("$.happy", "EQUALS", isHappy.get()));
                CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(Mail.ENTITY_NAME, ENTITY_VERSION, condition, true);
                ArrayNode nodes = filteredItemsFuture.get(10, TimeUnit.SECONDS);
                List<Mail> mails = convertNodesToMails(nodes);
                return ResponseEntity.ok(mails);
            }
        } catch (IllegalArgumentException e) {
            logger.error("Illegal argument: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            logger.error("Error retrieving mails: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private void processMail(UUID technicalId, Mail mail) {
        logger.info("Processing mail with technicalId {}", technicalId);

        boolean happyCriteria = checkMailHappy(mail);
        boolean gloomyCriteria = checkMailGloomy(mail);

        try {
            if (happyCriteria) {
                sendHappyMail(mail);
                mail.setStatus("SENT");
                logger.info("Happy mail sent to recipients: {}", mail.getMailList());
            } else if (gloomyCriteria) {
                sendGloomyMail(mail);
                mail.setStatus("SENT");
                logger.info("Gloomy mail sent to recipients: {}", mail.getMailList());
            } else {
                mail.setStatus("FAILED");
                logger.error("Mail does not meet happy or gloomy criteria");
            }
        } catch (Exception e) {
            mail.setStatus("FAILED");
            logger.error("Error sending mail: {}", e.getMessage());
        }

        // TODO: update entity in external service if needed (update not supported now)
    }

    private boolean checkMailHappy(Mail mail) {
        return mail.isHappy();
    }

    private boolean checkMailGloomy(Mail mail) {
        return !mail.isHappy();
    }

    private void sendHappyMail(Mail mail) {
        logger.info("Sending happy mail: {}", mail.getContent());
        // Actual sending logic omitted
    }

    private void sendGloomyMail(Mail mail) {
        logger.info("Sending gloomy mail: {}", mail.getContent());
        // Actual sending logic omitted
    }

    private Mail convertNodeToMail(ObjectNode node) {
        try {
            // convert ObjectNode to Mail using Jackson or manually
            // here assuming Mail class has Jackson annotations and default constructor
            return com.fasterxml.jackson.databind.json.JsonMapper.builder().build().treeToValue(node, Mail.class);
        } catch (Exception e) {
            logger.error("Error converting node to Mail: {}", e.getMessage());
            return null;
        }
    }

    private List<Mail> convertNodesToMails(ArrayNode nodes) {
        List<Mail> mails = new ArrayList<>();
        if (nodes == null) {
            return mails;
        }
        for (int i = 0; i < nodes.size(); i++) {
            ObjectNode node = (ObjectNode) nodes.get(i);
            Mail mail = convertNodeToMail(node);
            if (mail != null) {
                mails.add(mail);
            }
        }
        return mails;
    }
}