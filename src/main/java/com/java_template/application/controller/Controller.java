package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import static com.java_template.common.config.Config.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@RestController
@RequestMapping(path = "/mails")
@Slf4j
public class Controller {

    private final EntityService entityService;

    public Controller(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> createMail(@RequestBody Mail mail) {
        try {
            if (mail == null || mail.getIsHappy() == null || mail.getMailList() == null || mail.getMailList().isEmpty()) {
                log.error("Invalid mail entity received");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            // Add item via entityService
            CompletableFuture<UUID> idFuture = entityService.addItem(Mail.ENTITY_NAME, ENTITY_VERSION, mail);
            UUID technicalId = idFuture.get(5, TimeUnit.SECONDS);
            log.info("Mail entity saved with technicalId {}", technicalId);

            // Removed processMail call here as processMail method extracted

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId.toString());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            log.error("Invalid argument in createMail: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            log.error("Error creating mail: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            log.error("Unexpected error creating mail: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{technicalId}")
    public ResponseEntity<MailResponse> getMailById(@PathVariable String technicalId) {
        try {
            UUID technicalUUID = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Mail.ENTITY_NAME, ENTITY_VERSION, technicalUUID);
            ObjectNode node = itemFuture.get(5, TimeUnit.SECONDS);
            if (node == null || node.isEmpty()) {
                log.error("Mail not found with technicalId {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Boolean isHappy = node.has("isHappy") && !node.get("isHappy").isNull() ? node.get("isHappy").asBoolean() : null;
            List<String> mailList = new ArrayList<>();
            if (node.has("mailList") && node.get("mailList").isArray()) {
                node.get("mailList").forEach(jsonNode -> mailList.add(jsonNode.asText()));
            }
            String status = node.has("status") && !node.get("status").isNull() ? node.get("status").asText() : null;
            MailResponse response = new MailResponse(isHappy, mailList, status);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("Invalid technicalId format: {}", technicalId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            log.error("Error retrieving mail with technicalId {}: {}", technicalId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            log.error("Unexpected error retrieving mail with technicalId {}: {}", technicalId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping
    public ResponseEntity<List<MailResponseWithId>> getMailsByIsHappy(@RequestParam(required = false) Boolean isHappy) {
        try {
            if (isHappy == null) {
                return ResponseEntity.badRequest().build();
            }
            // Build condition
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.isHappy", "EQUALS", isHappy));
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(Mail.ENTITY_NAME, ENTITY_VERSION, condition, true);
            ArrayNode nodes = filteredItemsFuture.get(5, TimeUnit.SECONDS);
            List<MailResponseWithId> result = new ArrayList<>();
            if (nodes != null) {
                for (int i = 0; i < nodes.size(); i++) {
                    ObjectNode node = (ObjectNode) nodes.get(i);
                    String technicalId = node.has("technicalId") ? node.get("technicalId").asText() : null;
                    Boolean nodeIsHappy = node.has("isHappy") && !node.get("isHappy").isNull() ? node.get("isHappy").asBoolean() : null;
                    List<String> mailList = new ArrayList<>();
                    if (node.has("mailList") && node.get("mailList").isArray()) {
                        node.get("mailList").forEach(jsonNode -> mailList.add(jsonNode.asText()));
                    }
                    if (technicalId != null) {
                        result.add(new MailResponseWithId(technicalId, nodeIsHappy, mailList));
                    }
                }
            }
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            log.error("Invalid argument in getMailsByIsHappy: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            log.error("Error retrieving mails by isHappy {}: {}", isHappy, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            log.error("Unexpected error retrieving mails by isHappy {}: {}", isHappy, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Inner class to simulate mail sending
    static class MailSender {
        boolean sendEmails(List<String> recipients, String subject, String content) {
            try {
                for (String recipient : recipients) {
                    // Simulate sending email logic here; in real app use JavaMailSender
                }
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Mail {
        public static final String ENTITY_NAME = "Mail";

        private Boolean isHappy;
        private List<String> mailList;
        private String status;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class MailResponse {
        private Boolean isHappy;
        private List<String> mailList;
        private String status;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class MailResponseWithId {
        private String technicalId;
        private Boolean isHappy;
        private List<String> mailList;
    }
}