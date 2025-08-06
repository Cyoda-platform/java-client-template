package com.java_template.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.dto.SnapshotJobRequest;
import com.java_template.application.entity.SnapshotJob;
import com.java_template.application.entity.TeamSnapshot;
import com.java_template.application.entity.SquadSnapshot;
import com.java_template.common.service.EntityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import static com.java_template.common.config.Config.ENTITY_VERSION;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.UUID;

import jakarta.validation.Valid;

@RestController
@RequestMapping(path = "/entity")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    // ================= SnapshotJob Endpoints =================

    @PostMapping("/snapshotJob")
    public ResponseEntity<Map<String, String>> createSnapshotJob(@Valid @RequestBody SnapshotJobRequest request) throws InterruptedException, ExecutionException {
        try {
            // Validate date range
            LocalDate startDate, endDate;
            try {
                startDate = LocalDate.parse(request.getDateRangeStart(), DateTimeFormatter.ISO_LOCAL_DATE);
                endDate = LocalDate.parse(request.getDateRangeEnd(), DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (DateTimeParseException e) {
                log.error("Invalid date format in SnapshotJob request", e);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            if (!startDate.isBefore(endDate)) {
                log.error("dateRangeStart must be before dateRangeEnd");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            // Derive season from start date (assuming Bundesliga season starts in August)
            String season = String.valueOf(startDate.getMonthValue() >= 8 ? startDate.getYear() : startDate.getYear() - 1);

            // Create SnapshotJob entity
            SnapshotJob job = new SnapshotJob();
            job.setSeason(season);
            job.setDateRangeStart(request.getDateRangeStart());
            job.setDateRangeEnd(request.getDateRangeEnd());
            job.setStatus("PENDING");
            job.setCreatedAt(new Date().toInstant().toString());

            if (!job.isValid()) {
                log.error("Invalid SnapshotJob data after creation");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            CompletableFuture<UUID> idFuture = entityService.addItem(SnapshotJob.ENTITY_NAME, ENTITY_VERSION, job);
            UUID technicalId = idFuture.get();

            String technicalIdStr = "snapshotJob-" + technicalId.toString();

            log.info("Created SnapshotJob with id {} for season {}", technicalIdStr, season);

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalIdStr);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            log.error("Invalid argument when creating SnapshotJob", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to create SnapshotJob", e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error creating SnapshotJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/snapshotJob/{id}")
    public ResponseEntity<SnapshotJob> getSnapshotJob(@PathVariable String id) throws InterruptedException, ExecutionException, JsonProcessingException {
        try {
            UUID technicalId;
            try {
                technicalId = UUID.fromString(id.replaceFirst("^snapshotJob-", ""));
            } catch (IllegalArgumentException e) {
                log.error("Invalid SnapshotJob id format: {}", id);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(SnapshotJob.ENTITY_NAME, ENTITY_VERSION, technicalId);
            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                log.error("SnapshotJob not found: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            SnapshotJob job = objectMapper.treeToValue(node, SnapshotJob.class);
            return ResponseEntity.ok(job);
        } catch (IllegalArgumentException e) {
            log.error("Invalid argument when getting SnapshotJob", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to get SnapshotJob", e);
            throw e;
        } catch (JsonProcessingException e) {
            log.error("JSON processing error when getting SnapshotJob", e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error getting SnapshotJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ================= TeamSnapshot Endpoints =================

    @GetMapping("/teamSnapshot/{id}")
    public ResponseEntity<TeamSnapshot> getTeamSnapshot(@PathVariable String id) throws InterruptedException, ExecutionException, JsonProcessingException {
        try {
            UUID technicalId;
            try {
                technicalId = UUID.fromString(id.replaceFirst("^teamSnapshot-", ""));
            } catch (IllegalArgumentException e) {
                log.error("Invalid TeamSnapshot id format: {}", id);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(TeamSnapshot.ENTITY_NAME, ENTITY_VERSION, technicalId);
            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                log.error("TeamSnapshot not found: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            TeamSnapshot teamSnapshot = objectMapper.treeToValue(node, TeamSnapshot.class);
            return ResponseEntity.ok(teamSnapshot);
        } catch (IllegalArgumentException e) {
            log.error("Invalid argument when getting TeamSnapshot", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to get TeamSnapshot", e);
            throw e;
        } catch (JsonProcessingException e) {
            log.error("JSON processing error when getting TeamSnapshot", e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error getting TeamSnapshot", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ================= SquadSnapshot Endpoints =================

    @GetMapping("/squadSnapshot/{id}")
    public ResponseEntity<SquadSnapshot> getSquadSnapshot(@PathVariable String id) throws InterruptedException, ExecutionException, JsonProcessingException {
        try {
            UUID technicalId;
            try {
                technicalId = UUID.fromString(id.replaceFirst("^squadSnapshot-", ""));
            } catch (IllegalArgumentException e) {
                log.error("Invalid SquadSnapshot id format: {}", id);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(SquadSnapshot.ENTITY_NAME, ENTITY_VERSION, technicalId);
            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                log.error("SquadSnapshot not found: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            SquadSnapshot squadSnapshot = objectMapper.treeToValue(node, SquadSnapshot.class);
            return ResponseEntity.ok(squadSnapshot);
        } catch (IllegalArgumentException e) {
            log.error("Invalid argument when getting SquadSnapshot", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to get SquadSnapshot", e);
            throw e;
        } catch (JsonProcessingException e) {
            log.error("JSON processing error when getting SquadSnapshot", e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error getting SquadSnapshot", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

}