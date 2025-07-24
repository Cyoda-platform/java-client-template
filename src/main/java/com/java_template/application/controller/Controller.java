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

            // processWorkflow method removed

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

}