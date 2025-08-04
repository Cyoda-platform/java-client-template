package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import static com.java_template.common.config.Config.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.UUID;

@RestController
@RequestMapping(path = "/entity")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private final EntityService entityService;

    // ================= SnapshotJob Endpoints =================

    @PostMapping("/snapshotJob")
    public ResponseEntity<Map<String, String>> createSnapshotJob(@RequestBody SnapshotJob job) {
        try {
            if (!job.isValid()) {
                log.error("Invalid SnapshotJob data");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            job.setStatus("PENDING");
            job.setCreatedAt(new Date().toInstant().toString());

            CompletableFuture<UUID> idFuture = entityService.addItem(SnapshotJob.ENTITY_NAME, ENTITY_VERSION, job);
            UUID technicalId = idFuture.get();

            String technicalIdStr = "snapshotJob-" + technicalId.toString();

            log.info("Created SnapshotJob with id {}", technicalIdStr);

            processSnapshotJob(technicalIdStr, job);

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalIdStr);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            log.error("Invalid argument when creating SnapshotJob", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to create SnapshotJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            log.error("Unexpected error creating SnapshotJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/snapshotJob/{id}")
    public ResponseEntity<SnapshotJob> getSnapshotJob(@PathVariable String id) {
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
            SnapshotJob job = node.traverse().readValueAs(SnapshotJob.class);
            return ResponseEntity.ok(job);
        } catch (IllegalArgumentException e) {
            log.error("Invalid argument when getting SnapshotJob", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to get SnapshotJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            log.error("Unexpected error getting SnapshotJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ================= TeamSnapshot Endpoints =================

    @GetMapping("/teamSnapshot/{id}")
    public ResponseEntity<TeamSnapshot> getTeamSnapshot(@PathVariable String id) {
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
            TeamSnapshot teamSnapshot = node.traverse().readValueAs(TeamSnapshot.class);
            return ResponseEntity.ok(teamSnapshot);
        } catch (IllegalArgumentException e) {
            log.error("Invalid argument when getting TeamSnapshot", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to get TeamSnapshot", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            log.error("Unexpected error getting TeamSnapshot", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ================= SquadSnapshot Endpoints =================

    @GetMapping("/squadSnapshot/{id}")
    public ResponseEntity<SquadSnapshot> getSquadSnapshot(@PathVariable String id) {
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
            SquadSnapshot squadSnapshot = node.traverse().readValueAs(SquadSnapshot.class);
            return ResponseEntity.ok(squadSnapshot);
        } catch (IllegalArgumentException e) {
            log.error("Invalid argument when getting SquadSnapshot", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to get SquadSnapshot", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            log.error("Unexpected error getting SquadSnapshot", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }


    // ================= Process Methods =================

    private void processSnapshotJob(String technicalId, SnapshotJob job) {
        log.info("Processing SnapshotJob id: {}", technicalId);
        try {
            if (job.getDateRangeStart().isBlank() || job.getDateRangeEnd().isBlank() || job.getSeason().isBlank()) {
                log.error("SnapshotJob validation failed for id: {}", technicalId);
                job.setStatus("FAILED");
                updateSnapshotJobStatus(technicalId, job);
                return;
            }
            Date startDate = java.sql.Date.valueOf(job.getDateRangeStart());
            Date endDate = java.sql.Date.valueOf(job.getDateRangeEnd());
            if (!startDate.before(endDate)) {
                log.error("SnapshotJob dateRangeStart is not before dateRangeEnd for id: {}", technicalId);
                job.setStatus("FAILED");
                updateSnapshotJobStatus(technicalId, job);
                return;
            }

            // Simulated data fetch and entity creation:
            // Create one team snapshot as example
            TeamSnapshot teamSnapshot = new TeamSnapshot();
            teamSnapshot.setSeason(job.getSeason());
            teamSnapshot.setEffectiveDate(job.getDateRangeStart());
            teamSnapshot.setTeamId(1);
            teamSnapshot.setTeamName("FC Bayern München");
            teamSnapshot.setVenue("Allianz Arena");
            teamSnapshot.setCrestUrl("https://crests.example.com/fcbayern.png");
            teamSnapshot.setCreatedAt(new Date().toInstant().toString());

            CompletableFuture<UUID> teamSnapshotIdFuture = entityService.addItem(TeamSnapshot.ENTITY_NAME, ENTITY_VERSION, teamSnapshot);
            UUID teamSnapshotUUID = teamSnapshotIdFuture.get();
            String teamSnapshotId = "teamSnapshot-" + teamSnapshotUUID.toString();

            // Create one squad snapshot linked to above team snapshot
            SquadSnapshot squadSnapshot = new SquadSnapshot();
            squadSnapshot.setTeamSnapshotId(teamSnapshotId);
            squadSnapshot.setPlayerId(10);
            squadSnapshot.setPlayerName("Thomas Müller");
            squadSnapshot.setPosition("Midfielder");
            squadSnapshot.setDateOfBirth("1989-09-13");
            squadSnapshot.setNationality("German");
            squadSnapshot.setSquadNumber(25);
            squadSnapshot.setContractStartDate("2012-07-01");
            squadSnapshot.setContractEndDate("2024-06-30");
            squadSnapshot.setCreatedAt(new Date().toInstant().toString());

            CompletableFuture<UUID> squadSnapshotIdFuture = entityService.addItem(SquadSnapshot.ENTITY_NAME, ENTITY_VERSION, squadSnapshot);
            UUID squadSnapshotUUID = squadSnapshotIdFuture.get();
            // String squadSnapshotId = "squadSnapshot-" + squadSnapshotUUID.toString(); // not used

            // Change detection and notification logic omitted for prototype

            job.setStatus("COMPLETED");
            updateSnapshotJobStatus(technicalId, job);
            log.info("Completed processing SnapshotJob id: {}", technicalId);
        } catch (Exception e) {
            log.error("Error processing SnapshotJob id: {}", technicalId, e);
            job.setStatus("FAILED");
            try {
                updateSnapshotJobStatus(technicalId, job);
            } catch (Exception ex) {
                log.error("Failed to update SnapshotJob status to FAILED for id: {}", technicalId, ex);
            }
        }
    }

    private void updateSnapshotJobStatus(String technicalId, SnapshotJob job) throws Exception {
        // TODO: update operation not supported by EntityService, skipping update
        // Just log that update would happen here
        log.warn("TODO: update SnapshotJob status for id: {} to {}", technicalId, job.getStatus());
    }
}