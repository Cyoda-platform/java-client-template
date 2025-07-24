package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Workflow;
import com.java_template.application.entity.NBAGameScore;
import com.java_template.application.entity.EmailNotification;
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
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.UUID;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/entity")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final RestTemplate restTemplate = new RestTemplate();

    // POST /entity/workflow - create new Workflow entity (subscribe & request scores)
    @PostMapping("/workflow")
    public ResponseEntity<?> createWorkflow(@RequestBody Workflow workflowRequest) {
        try {
            if (workflowRequest == null || workflowRequest.getSubscriberEmail() == null || workflowRequest.getSubscriberEmail().isBlank()) {
                logger.error("Invalid subscriber email in workflow creation request");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "subscriberEmail is required and cannot be blank"));
            }
            if (workflowRequest.getRequestedDate() == null || workflowRequest.getRequestedDate().isBlank()) {
                logger.error("Invalid requestedDate in workflow creation request");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "requestedDate is required and cannot be blank"));
            }

            workflowRequest.setStatus("PENDING");
            workflowRequest.setCreatedAt(java.time.Instant.now().toString());

            CompletableFuture<UUID> idFuture = entityService.addItem("workflow", ENTITY_VERSION, workflowRequest);
            UUID technicalId = idFuture.get();

            logger.info("Created Workflow with technicalId: {}", technicalId);

            processWorkflow(technicalId, workflowRequest);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId.toString()));
        } catch (IllegalArgumentException e) {
            logger.error("Illegal argument in createWorkflow: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error in createWorkflow: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // GET /entity/workflow/{id} - retrieve Workflow entity by technicalId
    @GetMapping("/workflow/{id}")
    public ResponseEntity<?> getWorkflow(@PathVariable String id) {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem("workflow", ENTITY_VERSION, technicalId);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                logger.error("Workflow with ID {} not found", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Workflow not found"));
            }
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format in getWorkflow: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid UUID format"));
        } catch (Exception e) {
            logger.error("Error in getWorkflow: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // GET /entity/nbagames/{date} - retrieve all NBAGameScore entities for specific date
    @GetMapping("/nbagames/{date}")
    public ResponseEntity<?> getNBAGamesByDate(@PathVariable String date) {
        try {
            if (date == null || date.isBlank()) {
                logger.error("Invalid date parameter in getNBAGamesByDate");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Date parameter is required"));
            }

            Condition condition = Condition.of("$.gameDate", "EQUALS", date);
            SearchConditionRequest searchCondition = SearchConditionRequest.group("AND", condition);

            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition("nbagamescore", ENTITY_VERSION, searchCondition, true);
            ArrayNode items = filteredItemsFuture.get();

            List<Object> results = new ArrayList<>();
            for (var jsonNode : items) {
                results.add(jsonNode);
            }
            return ResponseEntity.ok(results);
        } catch (IllegalArgumentException e) {
            logger.error("Illegal argument in getNBAGamesByDate: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error in getNBAGamesByDate: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // GET /entity/emailnotification/{id} - retrieve EmailNotification entity by technicalId
    @GetMapping("/emailnotification/{id}")
    public ResponseEntity<?> getEmailNotification(@PathVariable String id) {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem("emailnotification", ENTITY_VERSION, technicalId);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                logger.error("EmailNotification with ID {} not found", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "EmailNotification not found"));
            }
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format in getEmailNotification: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid UUID format"));
        } catch (Exception e) {
            logger.error("Error in getEmailNotification: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // Process Workflow entity with business logic: fetch NBA scores, save NBAGameScore entities, create and send notifications
    private void processWorkflow(UUID workflowId, Workflow workflow) {
        try {
            logger.info("Processing Workflow with ID: {}", workflowId);

            workflow.setStatus("PROCESSING");
            entityService.addItem("workflow", ENTITY_VERSION, workflow).get(); // replace update with addItem? TODO: skip update

            // Validate email format simple check
            if (!workflow.getSubscriberEmail().contains("@")) {
                logger.error("Invalid subscriber email format: {}", workflow.getSubscriberEmail());
                workflow.setStatus("FAILED");
                entityService.addItem("workflow", ENTITY_VERSION, workflow).get(); // TODO: skip update
                return;
            }
            // Validate date format simple check (YYYY-MM-DD)
            if (!workflow.getRequestedDate().matches("\\d{4}-\\d{2}-\\d{2}")) {
                logger.error("Invalid requestedDate format: {}", workflow.getRequestedDate());
                workflow.setStatus("FAILED");
                entityService.addItem("workflow", ENTITY_VERSION, workflow).get(); // TODO: skip update
                return;
            }

            // Fetch NBA game scores asynchronously
            List<NBAGameScore> fetchedScores = fetchNBAScores(workflow.getRequestedDate());
            List<NBAGameScore> savedScores = new ArrayList<>();
            if (!fetchedScores.isEmpty()) {
                CompletableFuture<List<UUID>> idsFuture = entityService.addItems("nbagamescore", ENTITY_VERSION, fetchedScores);
                List<UUID> scoreIds = idsFuture.get();
                // We don't have the ability to update status, so skip storing technicalId mapping
                for (int i = 0; i < fetchedScores.size(); i++) {
                    NBAGameScore score = fetchedScores.get(i);
                    UUID scoreId = scoreIds.get(i);
                    processNBAGameScore(scoreId, score);
                }
            }

            // Create EmailNotification entity
            EmailNotification notification = new EmailNotification();
            notification.setSubscriberEmail(workflow.getSubscriberEmail());
            notification.setNotificationDate(workflow.getRequestedDate());
            notification.setEmailSentStatus("PENDING");
            notification.setSentAt(null);
            CompletableFuture<UUID> notificationIdFuture = entityService.addItem("emailnotification", ENTITY_VERSION, notification);
            UUID notificationId = notificationIdFuture.get();
            processEmailNotification(notificationId, notification);

            workflow.setStatus("COMPLETED");
            entityService.addItem("workflow", ENTITY_VERSION, workflow).get(); // TODO: skip update

            logger.info("Workflow {} processing COMPLETED", workflowId);
        } catch (Exception ex) {
            logger.error("Error processing Workflow {}: {}", workflowId, ex.getMessage(), ex);
            try {
                workflow.setStatus("FAILED");
                entityService.addItem("workflow", ENTITY_VERSION, workflow).get(); // TODO: skip update
            } catch (Exception e) {
                logger.error("Error updating workflow status to FAILED: {}", e.getMessage(), e);
            }
        }
    }

    // Fetch NBA scores from external API asynchronously
    private List<NBAGameScore> fetchNBAScores(String date) {
        try {
            logger.info("Fetching NBA scores for date: {}", date);
            String url = "https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/" + date + "?key=test";
            var headers = new org.springframework.http.HttpHeaders();
            headers.setAccept(List.of(org.springframework.http.MediaType.APPLICATION_JSON));
            var entity = new org.springframework.http.HttpEntity<String>(headers);
            NBAGameScore[] response = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, entity, NBAGameScore[].class).getBody();
            if (response == null) {
                logger.error("No data returned from NBA API for date {}", date);
                return Collections.emptyList();
            }
            logger.info("Fetched {} NBA games for date {}", response.length, date);
            return Arrays.asList(response);
        } catch (Exception e) {
            logger.error("Error fetching NBA scores: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private void processNBAGameScore(UUID scoreId, NBAGameScore score) {
        logger.info("Processing NBAGameScore with technicalId: {}", scoreId);
        // No extra processing required as per requirements (immutable records)
    }

    private void processEmailNotification(UUID notificationId, EmailNotification notification) {
        logger.info("Processing EmailNotification with technicalId: {}", notificationId);
        try {
            // Simulated email sending logic
            boolean emailSent = sendEmail(notification.getSubscriberEmail(), notification.getNotificationDate());
            if (emailSent) {
                notification.setEmailSentStatus("SENT");
                notification.setSentAt(java.time.Instant.now().toString());
                logger.info("Email sent successfully to {}", notification.getSubscriberEmail());
            } else {
                notification.setEmailSentStatus("FAILED");
                logger.error("Failed to send email to {}", notification.getSubscriberEmail());
            }
            entityService.addItem("emailnotification", ENTITY_VERSION, notification).get(); // TODO: skip update
        } catch (Exception ex) {
            notification.setEmailSentStatus("FAILED");
            try {
                entityService.addItem("emailnotification", ENTITY_VERSION, notification).get(); // TODO: skip update
            } catch (Exception e) {
                logger.error("Error updating EmailNotification status to FAILED: {}", e.getMessage(), e);
            }
            logger.error("Exception while sending email to {}: {}", notification.getSubscriberEmail(), ex.getMessage(), ex);
        }
    }

    // Dummy email sending method
    private boolean sendEmail(String toEmail, String notificationDate) {
        // Simulate email sending logic here, always returns true for prototype
        logger.info("Sending email to {} with NBA scores for date {}", toEmail, notificationDate);
        return true;
    }
}