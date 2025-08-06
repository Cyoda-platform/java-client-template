package com.java_template.prototype;

import com.java_template.application.dto.SnapshotJobRequest;
import com.java_template.application.entity.SnapshotJob;
import com.java_template.application.entity.SquadSnapshot;
import com.java_template.application.entity.TeamSnapshot;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    // Caches and ID counters for entities
    private final ConcurrentHashMap<String, SnapshotJob> snapshotJobCache = new ConcurrentHashMap<>();
    private final AtomicLong snapshotJobIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, TeamSnapshot> teamSnapshotCache = new ConcurrentHashMap<>();
    private final AtomicLong teamSnapshotIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, SquadSnapshot> squadSnapshotCache = new ConcurrentHashMap<>();
    private final AtomicLong squadSnapshotIdCounter = new AtomicLong(1);

    // ================= SnapshotJob Endpoints =================

    @PostMapping("/snapshotJob")
    public ResponseEntity<Map<String, String>> createSnapshotJob(@Valid @RequestBody SnapshotJobRequest request) {
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

            String technicalId = "snapshotJob-" + snapshotJobIdCounter.getAndIncrement();
            snapshotJobCache.put(technicalId, job);

            log.info("Created SnapshotJob with id {} for season {}", technicalId, season);

            processSnapshotJob(technicalId, job);

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.error("Failed to create SnapshotJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/snapshotJob/{id}")
    public ResponseEntity<SnapshotJob> getSnapshotJob(@PathVariable String id) {
        SnapshotJob job = snapshotJobCache.get(id);
        if (job == null) {
            log.error("SnapshotJob not found: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(job);
    }

    // ================= TeamSnapshot Endpoints =================

    @GetMapping("/teamSnapshot/{id}")
    public ResponseEntity<TeamSnapshot> getTeamSnapshot(@PathVariable String id) {
        TeamSnapshot teamSnapshot = teamSnapshotCache.get(id);
        if (teamSnapshot == null) {
            log.error("TeamSnapshot not found: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(teamSnapshot);
    }

    // ================= SquadSnapshot Endpoints =================

    @GetMapping("/squadSnapshot/{id}")
    public ResponseEntity<SquadSnapshot> getSquadSnapshot(@PathVariable String id) {
        SquadSnapshot squadSnapshot = squadSnapshotCache.get(id);
        if (squadSnapshot == null) {
            log.error("SquadSnapshot not found: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(squadSnapshot);
    }

    // ================= Process Methods =================

    private void processSnapshotJob(String technicalId, SnapshotJob job) {
        log.info("Processing SnapshotJob id: {}", technicalId);
        try {
            // Validation: dateRangeStart < dateRangeEnd and season format
            if (job.getDateRangeStart().isBlank() || job.getDateRangeEnd().isBlank() || job.getSeason().isBlank()) {
                String failureReason = "SnapshotJob validation failed: missing required fields (dateRangeStart, dateRangeEnd, or season)";
                log.error("SnapshotJob validation failed for id: {}", technicalId);
                job.setStatus("FAILED");
                job.setFailReason(failureReason);
                snapshotJobCache.put(technicalId, job);
                return;
            }
            Date startDate = java.sql.Date.valueOf(job.getDateRangeStart());
            Date endDate = java.sql.Date.valueOf(job.getDateRangeEnd());
            if (!startDate.before(endDate)) {
                String failureReason = "SnapshotJob date validation failed: dateRangeStart (" + job.getDateRangeStart() + ") must be before dateRangeEnd (" + job.getDateRangeEnd() + ")";
                log.error("SnapshotJob dateRangeStart is not before dateRangeEnd for id: {}", technicalId);
                job.setStatus("FAILED");
                job.setFailReason(failureReason);
                snapshotJobCache.put(technicalId, job);
                return;
            }

            // Processing:
            // 1. Fetch teams for the season from football-data.org API
            // 2. For each team, create immutable TeamSnapshot for effectiveDate(s)
            // 3. For each team, fetch squad data and create SquadSnapshot entities
            // For prototype, simulate these steps

            // Simulated data fetch and entity creation:
            // Create one team snapshot as example
            String teamSnapshotId = "teamSnapshot-" + teamSnapshotIdCounter.getAndIncrement();
            TeamSnapshot teamSnapshot = new TeamSnapshot();
            teamSnapshot.setSeason(job.getSeason());
            teamSnapshot.setEffectiveDate(job.getDateRangeStart());
            teamSnapshot.setTeamId(1);
            teamSnapshot.setTeamName("FC Bayern München");
            teamSnapshot.setVenue("Allianz Arena");
            teamSnapshot.setCrestUrl("https://crests.example.com/fcbayern.png");
            teamSnapshot.setCreatedAt(new Date().toInstant().toString());
            teamSnapshotCache.put(teamSnapshotId, teamSnapshot);

            // Create one squad snapshot linked to above team snapshot
            String squadSnapshotId = "squadSnapshot-" + squadSnapshotIdCounter.getAndIncrement();
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
            squadSnapshotCache.put(squadSnapshotId, squadSnapshot);

            // Change detection and notification logic omitted for prototype

            job.setStatus("COMPLETED");
            snapshotJobCache.put(technicalId, job);
            log.info("Completed processing SnapshotJob id: {}", technicalId);
        } catch (Exception e) {
            log.error("Error processing SnapshotJob id: {}", technicalId, e);
            job.setStatus("FAILED");
            snapshotJobCache.put(technicalId, job);
        }
    }
}