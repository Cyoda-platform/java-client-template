package com.java_template.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Report;
import com.java_template.application.entity.ReportJob;
import com.java_template.common.service.EntityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping(path = "/entity")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/reportJob")
    public ResponseEntity<Map<String, String>> createReportJob(@RequestBody(required = false) Map<String, Object> requestBody) {
        try {
            ReportJob reportJob = new ReportJob();

            reportJob.setBtcUsdRate(BigDecimal.ZERO);
            reportJob.setBtcEurRate(BigDecimal.ZERO);
            reportJob.setTimestamp(OffsetDateTime.now());
            reportJob.setEmailStatus("PENDING");

            CompletableFuture<UUID> idFuture = entityService.addItem("ReportJob", ENTITY_VERSION, reportJob);
            UUID technicalId = idFuture.get();

            String technicalIdStr = technicalId.toString();

            logger.info("Created ReportJob with id {}", technicalIdStr);
            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalIdStr);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument while creating ReportJob", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Failed to create ReportJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error creating ReportJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/reportJob/{id}")
    public ResponseEntity<ReportJob> getReportJob(@PathVariable("id") String id) throws JsonProcessingException {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem("ReportJob", ENTITY_VERSION, technicalId);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                logger.error("ReportJob not found with id {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            ReportJob reportJob = objectMapper.treeToValue(node, ReportJob.class);
            return ResponseEntity.ok(reportJob);

        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format for ReportJob id {}", id, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Failed to retrieve ReportJob with id {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/report/{id}")
    public ResponseEntity<Report> getReport(@PathVariable("id") String id) throws JsonProcessingException {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem("Report", ENTITY_VERSION, technicalId);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                logger.error("Report not found with id {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Report report = objectMapper.treeToValue(node, Report.class);
            return ResponseEntity.ok(report);

        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format for Report id {}", id, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Failed to retrieve Report with id {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}