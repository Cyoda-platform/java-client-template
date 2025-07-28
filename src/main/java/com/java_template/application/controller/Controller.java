package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.CompanyData;
import com.java_template.application.entity.RetrievalJob;
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.UUID;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/entity")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    private final AtomicLong retrievalJobIdCounter = new AtomicLong(1);
    private final AtomicLong companyDataIdCounter = new AtomicLong(1);

    // POST /entity/retrievalJob - create a new RetrievalJob
    @PostMapping("/retrievalJob")
    public ResponseEntity<Map<String, String>> createRetrievalJob(@RequestBody RetrievalJob retrievalJob) {
        try {
            if (retrievalJob.getCompanyName() == null || retrievalJob.getCompanyName().isBlank()) {
                logger.error("createRetrievalJob failed: companyName is required");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "companyName is required"));
            }
            retrievalJob.setStatus("PENDING");

            // Add RetrievalJob entity to EntityService
            CompletableFuture<UUID> idFuture = entityService.addItem("RetrievalJob", ENTITY_VERSION, retrievalJob);
            UUID technicalId = idFuture.get();
            String techIdStr = technicalId.toString();
            logger.info("Created RetrievalJob with technicalId {}", techIdStr);

            // Trigger processRetrievalJob asynchronously
            // processRetrievalJob(technicalId, retrievalJob);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", techIdStr));
        } catch (IllegalArgumentException e) {
            logger.error("createRetrievalJob failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("createRetrievalJob failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // GET /entity/retrievalJob/{id} - get RetrievalJob by technicalId
    @GetMapping("/retrievalJob/{id}")
    public ResponseEntity<Object> getRetrievalJob(@PathVariable String id) {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem("RetrievalJob", ENTITY_VERSION, technicalId);
            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                logger.error("RetrievalJob with id {} not found", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "RetrievalJob not found"));
            }
            RetrievalJob job = node.traverse().readValueAs(RetrievalJob.class);
            return ResponseEntity.ok(job);
        } catch (IllegalArgumentException e) {
            logger.error("getRetrievalJob failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (ExecutionException e) {
            if (e.getCause() instanceof NoSuchElementException) {
                logger.error("RetrievalJob with id {} not found", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "RetrievalJob not found"));
            }
            logger.error("getRetrievalJob failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        } catch (Exception e) {
            logger.error("getRetrievalJob failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // GET /entity/companyData/{id} - get CompanyData by technicalId
    @GetMapping("/companyData/{id}")
    public ResponseEntity<Object> getCompanyData(@PathVariable String id) {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem("CompanyData", ENTITY_VERSION, technicalId);
            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                logger.error("CompanyData with id {} not found", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "CompanyData not found"));
            }
            CompanyData companyData = node.traverse().readValueAs(CompanyData.class);
            return ResponseEntity.ok(companyData);
        } catch (IllegalArgumentException e) {
            logger.error("getCompanyData failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (ExecutionException e) {
            if (e.getCause() instanceof NoSuchElementException) {
                logger.error("CompanyData with id {} not found", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "CompanyData not found"));
            }
            logger.error("getCompanyData failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        } catch (Exception e) {
            logger.error("getCompanyData failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // Optional GET /entity/companyData?businessId=xxx - search by businessId
    @GetMapping(value = "/companyData", params = "businessId")
    public ResponseEntity<List<CompanyData>> getCompanyDataByBusinessId(@RequestParam String businessId) {
        try {
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.businessId", "EQUALS", businessId));

            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    "CompanyData", ENTITY_VERSION, condition, true);
            ArrayNode arrayNode = filteredItemsFuture.get();

            if (arrayNode == null || arrayNode.isEmpty()) {
                logger.error("No CompanyData found with businessId {}", businessId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Collections.emptyList());
            }

            List<CompanyData> results = new ArrayList<>();
            for (int i = 0; i < arrayNode.size(); i++) {
                ObjectNode node = (ObjectNode) arrayNode.get(i);
                CompanyData cd = node.traverse().readValueAs(CompanyData.class);
                results.add(cd);
            }
            return ResponseEntity.ok(results);
        } catch (IllegalArgumentException e) {
            logger.error("getCompanyDataByBusinessId failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Collections.emptyList());
        } catch (Exception e) {
            logger.error("getCompanyDataByBusinessId failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Collections.emptyList());
        }
    }

}