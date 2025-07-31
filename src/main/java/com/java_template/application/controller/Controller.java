package com.java_template.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Mail;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/mail")
@Slf4j
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("")
    public ResponseEntity<?> createMail(@RequestBody MailRequest mailRequest) throws JsonProcessingException {
        try {
            if (mailRequest.getMailList() == null || mailRequest.getMailList().isEmpty()) {
                logger.error("Mail list is empty");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Mail list cannot be empty");
            }
            if (mailRequest.getContent() == null || mailRequest.getContent().isBlank()) {
                logger.error("Content is blank");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Content cannot be blank");
            }

            Mail mail = new Mail();
            mail.setMailList(mailRequest.getMailList());
            mail.setContent(mailRequest.getContent());
            mail.setMoodCriteriaChecked(false);
            mail.setIsHappy(null);

            CompletableFuture<UUID> idFuture = entityService.addItem(Mail.ENTITY_NAME, ENTITY_VERSION, mail);
            UUID technicalId = idFuture.get();
            String technicalIdStr = technicalId.toString();

            // Retrieve stored mail to process it
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Mail.ENTITY_NAME, ENTITY_VERSION, technicalId);
            ObjectNode storedMailNode = itemFuture.get();

            Mail storedMail = objectMapper.treeToValue(storedMailNode, Mail.class);
            // processMail method removed for workflow extraction

            // After processing, update the mail with new mood fields - update is not supported, so add TODO
            // TODO: Implement update method to update mood fields in external service

            logger.info("Mail processed successfully with technicalId {}", technicalIdStr);

            return ResponseEntity.status(HttpStatus.CREATED).body(new TechnicalIdResponse(technicalIdStr));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error processing mail: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Processing error");
        } catch (Exception e) {
            logger.error("Unexpected error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Processing error");
        }
    }

    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getMailById(@PathVariable String technicalId) throws JsonProcessingException {
        try {
            UUID technicalUUID = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Mail.ENTITY_NAME, ENTITY_VERSION, technicalUUID);
            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                logger.error("Mail not found for technicalId {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Mail not found");
            }
            Mail mail = objectMapper.treeToValue(node, Mail.class);
            MailResponse response = new MailResponse();
            response.setTechnicalId(technicalId);
            response.setMailList(mail.getMailList());
            response.setContent(mail.getContent());
            response.setIsHappy(mail.getIsHappy());
            response.setMoodCriteriaChecked(mail.getMoodCriteriaChecked());
            response.setStatus("SENT");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid technicalId format: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid technicalId format");
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error retrieving mail: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Retrieval error");
        } catch (Exception e) {
            logger.error("Unexpected error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Retrieval error");
        }
    }

    @GetMapping(params = "isHappy")
    public ResponseEntity<?> getMailsByMood(@RequestParam Boolean isHappy) throws JsonProcessingException {
        try {
            Condition condition = Condition.of("$.isHappy", "EQUALS", isHappy);
            SearchConditionRequest conditionRequest = SearchConditionRequest.group("AND", condition);

            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(Mail.ENTITY_NAME, ENTITY_VERSION, conditionRequest, true);
            ArrayNode filteredNodes = filteredItemsFuture.get();

            List<MailResponse> filteredMails = 
                filteredNodes.findValuesAsText("technicalId").stream()
                .map(tidStr -> {
                    try {
                        UUID tid = UUID.fromString(tidStr);
                        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Mail.ENTITY_NAME, ENTITY_VERSION, tid);
                        ObjectNode node = itemFuture.get();
                        Mail mail = objectMapper.treeToValue(node, Mail.class);
                        MailResponse response = new MailResponse();
                        response.setTechnicalId(tidStr);
                        response.setMailList(mail.getMailList());
                        response.setContent(mail.getContent());
                        response.setIsHappy(mail.getIsHappy());
                        response.setMoodCriteriaChecked(mail.getMoodCriteriaChecked());
                        response.setStatus("SENT");
                        return response;
                    } catch (Exception e) {
                        logger.error("Error processing mail with technicalId {}: {}", tidStr, e.getMessage());
                        return null;
                    }
                })
                .filter(r -> r != null)
                .collect(Collectors.toList());

            return ResponseEntity.ok(filteredMails);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error retrieving mails: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Retrieval error");
        } catch (Exception e) {
            logger.error("Unexpected error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Retrieval error");
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MailRequest {
        private List<String> mailList;
        private String content;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TechnicalIdResponse {
        private String technicalId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MailResponse {
        private String technicalId;
        private List<String> mailList;
        private String content;
        private Boolean isHappy;
        private Boolean moodCriteriaChecked;
        private String status;
    }
}