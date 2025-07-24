package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Mail;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/mails")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private final EntityService entityService;
    private static final String ENTITY_NAME = "Mail";

    @PostMapping
    public ResponseEntity<?> createMail(@RequestBody Mail mail) {
        try {
            log.info("Received request to create Mail");

            if (mail.getIsHappy() == null) {
                log.error("Validation failed: isHappy is null");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "isHappy is required"));
            }
            if (mail.getMailList() == null || mail.getMailList().isEmpty()) {
                log.error("Validation failed: mailList is empty or null");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "mailList must contain at least one email"));
            }
            for (String email : mail.getMailList()) {
                if (email == null || email.isBlank()) {
                    log.error("Validation failed: mailList contains blank email");
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "mailList cannot contain blank emails"));
                }
            }
            if (mail.getContent() == null || mail.getContent().isBlank()) {
                log.error("Validation failed: content is blank");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "content is required"));
            }

            mail.setStatus("PENDING");

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    ENTITY_NAME,
                    ENTITY_VERSION,
                    mail
            );

            UUID technicalId = idFuture.join();

            Mail createdMail = getMailByTechnicalId(technicalId.toString());

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", technicalId.toString()));
        } catch (IllegalArgumentException e) {
            log.error("IllegalArgumentException in createMail: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Exception in createMail", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getMail(@PathVariable String id) {
        try {
            // Try to get by technicalId first
            Mail mail = getMailByTechnicalId(id);
            if (mail != null) {
                return ResponseEntity.ok(mail);
            }

            // Try to get by business id
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.id", "EQUALS", id));
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                    ENTITY_NAME, ENTITY_VERSION, condition, true);
            ArrayNode filteredItems = itemsFuture.join();
            if (filteredItems != null && filteredItems.size() > 0) {
                ObjectNode node = (ObjectNode) filteredItems.get(0);
                Mail result = node.traverse(entityService.getObjectMapper()).readValueAs(Mail.class);
                return ResponseEntity.ok(result);
            }

            log.error("Mail with id {} not found by business id or technical id", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Mail not found"));
        } catch (IllegalArgumentException e) {
            log.error("IllegalArgumentException in getMail: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Exception in getMail", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    private Mail getMailByTechnicalId(String technicalId) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    ENTITY_NAME,
                    ENTITY_VERSION,
                    UUID.fromString(technicalId));
            ObjectNode node = itemFuture.join();
            if (node == null || node.isEmpty()) {
                return null;
            }
            return node.traverse(entityService.getObjectMapper()).readValueAs(Mail.class);
        } catch (Exception e) {
            return null;
        }
    }
}