package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Mail;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
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
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/mails")
@Slf4j
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    public Controller(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> createMail(@RequestBody Mail mail) {
        if (mail == null || !mail.isValid()) {
            logger.error("Invalid Mail entity received for creation");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        mail.setStatus("PENDING");

        try {
            CompletableFuture<UUID> idFuture = entityService.addItem(Mail.ENTITY_NAME, ENTITY_VERSION, mail);
            UUID technicalId = idFuture.get(5, TimeUnit.SECONDS);

            // After creation, process mail asynchronously
            // processMailAsync removed

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId.toString());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument while creating mail", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            logger.error("Failed to create mail", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{technicalId}")
    public ResponseEntity<Mail> getMailById(@PathVariable String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Mail.ENTITY_NAME, ENTITY_VERSION, id);
            ObjectNode node = itemFuture.get(5, TimeUnit.SECONDS);
            if (node == null) {
                logger.error("Mail not found for technicalId: {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Mail mail = node.traverse().readValueAs(Mail.class);
            return ResponseEntity.ok(mail);

        } catch (IllegalArgumentException e) {
            logger.error("Invalid technicalId format: {}", technicalId, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            logger.error("Failed to retrieve mail for technicalId: {}", technicalId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping(params = "isHappy")
    public ResponseEntity<List<Mail>> getMailsByIsHappy(@RequestParam boolean isHappy) {
        try {
            Condition condition = Condition.of("$.isHappy", "EQUALS", isHappy);
            SearchConditionRequest searchCondition = SearchConditionRequest.group("AND", condition);

            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(Mail.ENTITY_NAME, ENTITY_VERSION, searchCondition, true);
            ArrayNode arrayNode = filteredItemsFuture.get(5, TimeUnit.SECONDS);

            List<Mail> filteredMails = new ArrayList<>();
            if (arrayNode != null) {
                filteredMails = new ArrayList<>(arrayNode.size());
                for (int i = 0; i < arrayNode.size(); i++) {
                    ObjectNode node = (ObjectNode) arrayNode.get(i);
                    Mail mail = node.traverse().readValueAs(Mail.class);
                    filteredMails.add(mail);
                }
            }
            return ResponseEntity.ok(filteredMails);

        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument for getMailsByIsHappy", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            logger.error("Failed to retrieve mails by isHappy", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

}