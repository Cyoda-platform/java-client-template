package com.java_template.prototype;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.GameScoreData;
import com.java_template.application.entity.Job;
import com.java_template.application.entity.Subscriber;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Validated
@RestController
@RequestMapping(path = "/prototype/job")
@RequiredArgsConstructor
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;

    // ======= JOB CRUD via entityService =======

    @PostMapping
    public ResponseEntity<Map<String, Object>> createJob(@RequestBody @Valid JobDTO jobDTO) {
        Job job = fromJobDTO(jobDTO);
        try {
            CompletableFuture<UUID> idFuture = entityService.addItem("Job", ENTITY_VERSION, job);
            UUID technicalId = idFuture.join();
            job.setTechnicalId(technicalId);
            logger.info("Job created with technicalId {}", technicalId);
            return ResponseEntity.ok(Map.of("id", technicalId.toString(), "status", "processed"));
        } catch (Exception e) {
            logger.error("Error creating Job: {}", e.getMessage(), e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "Error creating Job");
        }
    }

    @GetMapping
    public ResponseEntity<Job> getJob(@RequestParam @NotBlank String id) {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (Exception e) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Invalid UUID format for id");
        }
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem("Job", ENTITY_VERSION, technicalId);
        ObjectNode node = itemFuture.join();
        if (node == null || node.isEmpty()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,
                    "Job with id " + id + " not found");
        }
        Job job = mapObjectNodeToJob(node);
        logger.info("Job retrieved with technicalId {}", id);
        return ResponseEntity.ok(job);
    }

    @PutMapping
    public ResponseEntity<Map<String, Object>> updateJob(@RequestBody @Valid JobUpdateDTO jobUpdateDTO) {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(jobUpdateDTO.getId());
        } catch (Exception e) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Invalid UUID format for id");
        }
        CompletableFuture<ObjectNode> existingItemFuture = entityService.getItem("Job", ENTITY_VERSION, technicalId);
        ObjectNode existingNode = existingItemFuture.join();
        if (existingNode == null || existingNode.isEmpty()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,
                    "Job with id " + jobUpdateDTO.getId() + " not found");
        }
        Job existingJob = mapObjectNodeToJob(existingNode);
        Job updatedJob = fromJobUpdateDTO(jobUpdateDTO, existingJob);
        try {
            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem("Job", ENTITY_VERSION, technicalId, updatedJob);
            UUID updatedTechnicalId = updatedIdFuture.join();
            processJob(updatedJob);
            logger.info("Job updated with technicalId {}", updatedTechnicalId);
            return ResponseEntity.ok(Map.of("id", updatedTechnicalId.toString(), "status", "processed"));
        } catch (Exception e) {
            logger.error("Error updating Job: {}", e.getMessage(), e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "Error updating Job");
        }
    }

    @DeleteMapping
    public ResponseEntity<Map<String, String>> deleteJob(@RequestParam @NotBlank String id) {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (Exception e) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Invalid UUID format for id");
        }
        try {
            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem("Job", ENTITY_VERSION, technicalId);
            UUID deletedTechnicalId = deletedIdFuture.join();
            logger.info("Job deleted with technicalId {}", deletedTechnicalId);
            return ResponseEntity.ok(Map.of("id", deletedTechnicalId.toString(), "status", "deleted"));
        } catch (Exception e) {
            logger.error("Error deleting Job: {}", e.getMessage(), e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,
                    "Job with id " + id + " not found");
        }
    }

    // ============================
    // Business logic for Job event processing simulation
    // ============================

    private void processJob(Job job) {
        logger.info("Processing Job event: technicalId={} name={}", job.getTechnicalId(), job.getName());
        // TODO: Add business logic for job processing simulation
    }

    // ============================
    // DTOs with validation
    // ============================

    @Data
    public static class JobDTO {
        @NotBlank
        private String name;

        @NotBlank
        private String schedule; // e.g. cron expression

        @NotBlank
        private String description;
    }

    @Data
    public static class JobUpdateDTO {
        @NotBlank
        private String id;

        @NotBlank
        private String name;

        @NotBlank
        private String schedule;

        @NotBlank
        private String description;
    }

    // ============================
    // Conversion helpers DTO -> Entity
    // ============================

    private Job fromJobDTO(JobDTO dto) {
        Job job = new Job();
        job.setName(dto.getName());
        job.setSchedule(dto.getSchedule());
        job.setDescription(dto.getDescription());
        return job;
    }

    private Job fromJobUpdateDTO(JobUpdateDTO dto, Job existing) {
        existing.setName(dto.getName());
        existing.setSchedule(dto.getSchedule());
        existing.setDescription(dto.getDescription());
        return existing;
    }

    private Job mapObjectNodeToJob(ObjectNode node) {
        Job job = new Job();
        if (node.has("technicalId") && !node.get("technicalId").isNull()) {
            job.setTechnicalId(UUID.fromString(node.get("technicalId").asText()));
        }
        if (node.has("name")) {
            job.setName(node.get("name").asText());
        }
        if (node.has("schedule")) {
            job.setSchedule(node.get("schedule").asText());
        }
        if (node.has("description")) {
            job.setDescription(node.get("description").asText());
        }
        // id field is not persisted in entityService, but if needed can be set from technicalId string
        job.setId(job.getTechnicalId() != null ? job.getTechnicalId().toString() : null);
        return job;
    }

}



// Subscriber controller - local cache as minor entity

package com.java_template.prototype;

import com.java_template.application.entity.Subscriber;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Validated
@RestController
@RequestMapping(path = "/prototype/subscriber")
@Slf4j
public class SubscriberControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(SubscriberControllerPrototype.class);

    private final ConcurrentHashMap<String, List<Subscriber>> subscriberCache = new ConcurrentHashMap<>();

    private final AtomicLong subscriberIdCounter = new AtomicLong(1);

    public SubscriberControllerPrototype() {
        subscriberCache.putIfAbsent("entities", Collections.synchronizedList(new ArrayList<>()));
        logger.info("SubscriberControllerPrototype initialized with empty cache.");
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createSubscriber(@RequestBody @Valid SubscriberDTO subscriberDTO) {
        Subscriber subscriber = fromSubscriberDTO(subscriberDTO);
        String id = addSubscriber(subscriber);
        logger.info("Subscriber created with id {}", id);
        return ResponseEntity.ok(Map.of("id", id, "status", "processed"));
    }

    @GetMapping
    public ResponseEntity<Subscriber> getSubscriber(@RequestParam @NotBlank String id) {
        Subscriber subscriber = getEntityById(id)
                .orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,
                        "Subscriber with id " + id + " not found"));
        return ResponseEntity.ok(subscriber);
    }

    @PutMapping
    public ResponseEntity<Map<String, Object>> updateSubscriber(@RequestBody @Valid SubscriberUpdateDTO subscriberUpdateDTO) {
        Subscriber existingSubscriber = getEntityById(subscriberUpdateDTO.getId())
                .orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,
                        "Subscriber with id " + subscriberUpdateDTO.getId() + " not found"));
        Subscriber updatedSubscriber = fromSubscriberUpdateDTO(subscriberUpdateDTO, existingSubscriber);
        updateEntity(updatedSubscriber.getId(), updatedSubscriber);
        processSubscriber(updatedSubscriber);
        logger.info("Subscriber updated with id {}", updatedSubscriber.getId());
        return ResponseEntity.ok(Map.of("id", updatedSubscriber.getId(), "status", "processed"));
    }

    @DeleteMapping
    public ResponseEntity<Map<String, String>> deleteSubscriber(@RequestParam @NotBlank String id) {
        if (deleteEntity(id)) {
            logger.info("Subscriber deleted with id {}", id);
            return ResponseEntity.ok(Map.of("id", id, "status", "deleted"));
        } else {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,
                    "Subscriber with id " + id + " not found");
        }
    }

    private String addSubscriber(Subscriber subscriber) {
        String id = String.valueOf(subscriberIdCounter.getAndIncrement());
        subscriber.setId(id);
        if (subscriber.getTechnicalId() == null) subscriber.setTechnicalId(UUID.randomUUID());
        subscriberCache.computeIfAbsent("entities", k -> Collections.synchronizedList(new ArrayList<>())).add(subscriber);
        processSubscriber(subscriber);
        return id;
    }

    private Optional<Subscriber> getEntityById(String id) {
        List<Subscriber> entities = subscriberCache.get("entities");
        if (entities == null) return Optional.empty();

        synchronized (entities) {
            return entities.stream()
                    .filter(entity -> id.equals(entity.getId()))
                    .findFirst();
        }
    }

    private void updateEntity(String id, Subscriber updatedEntity) {
        List<Subscriber> entities = subscriberCache.get("entities");
        if (entities == null) throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,
                "Subscriber with id " + id + " not found");
        synchronized (entities) {
            for (int i = 0; i < entities.size(); i++) {
                Subscriber entity = entities.get(i);
                if (id.equals(entity.getId())) {
                    updatedEntity.setId(id);
                    updatedEntity.setTechnicalId(entity.getTechnicalId());
                    entities.set(i, updatedEntity);
                    return;
                }
            }
        }
        throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,
                "Subscriber with id " + id + " not found");
    }

    private boolean deleteEntity(String id) {
        List<Subscriber> entities = subscriberCache.get("entities");
        if (entities == null) return false;
        synchronized (entities) {
            Iterator<Subscriber> iterator = entities.iterator();
            while (iterator.hasNext()) {
                Subscriber entity = iterator.next();
                if (id.equals(entity.getId())) {
                    iterator.remove();
                    return true;
                }
            }
        }
        return false;
    }

    private void processSubscriber(Subscriber subscriber) {
        logger.info("Processing Subscriber event: id={} email={}", subscriber.getId(), subscriber.getEmail());
        // TODO: Add business logic for subscriber event processing simulation
    }

    @Data
    public static class SubscriberDTO {
        @NotBlank
        @Pattern(regexp = "^[\\w-.]+@[\\w-]+\\.[\\w-.]+$", message = "Invalid email format")
        private String email;

        @NotBlank
        private String status; // e.g. "ACTIVE", "INACTIVE"
    }

    @Data
    public static class SubscriberUpdateDTO {
        @NotBlank
        private String id;

        @NotBlank
        @Pattern(regexp = "^[\\w-.]+@[\\w-]+\\.[\\w-.]+$", message = "Invalid email format")
        private String email;

        @NotBlank
        private String status;
    }

    private Subscriber fromSubscriberDTO(SubscriberDTO dto) {
        Subscriber subscriber = new Subscriber();
        subscriber.setEmail(dto.getEmail());
        subscriber.setStatus(dto.getStatus());
        return subscriber;
    }

    private Subscriber fromSubscriberUpdateDTO(SubscriberUpdateDTO dto, Subscriber existing) {
        existing.setEmail(dto.getEmail());
        existing.setStatus(dto.getStatus());
        return existing;
    }
}



// GameScoreData controller - local cache as minor entity

package com.java_template.prototype;

import com.java_template.application.entity.GameScoreData;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Validated
@RestController
@RequestMapping(path = "/prototype/gameScoreData")
@Slf4j
public class GameScoreDataControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(GameScoreDataControllerPrototype.class);

    private final ConcurrentHashMap<String, List<GameScoreData>> gameScoreDataCache = new ConcurrentHashMap<>();

    private final AtomicLong gameScoreDataIdCounter = new AtomicLong(1);

    public GameScoreDataControllerPrototype() {
        gameScoreDataCache.putIfAbsent("entities", Collections.synchronizedList(new ArrayList<>()));
        logger.info("GameScoreDataControllerPrototype initialized with empty cache.");
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createGameScoreData(@RequestBody @Valid GameScoreDataDTO gameScoreDataDTO) {
        GameScoreData gameScoreData = fromGameScoreDataDTO(gameScoreDataDTO);
        String id = addGameScoreData(gameScoreData);
        logger.info("GameScoreData created with id {}", id);
        return ResponseEntity.ok(Map.of("id", id, "status", "processed"));
    }

    @GetMapping
    public ResponseEntity<GameScoreData> getGameScoreData(@RequestParam @NotBlank String id) {
        GameScoreData gameScoreData = getEntityById(id)
                .orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,
                        "GameScoreData with id " + id + " not found"));
        return ResponseEntity.ok(gameScoreData);
    }

    @PutMapping
    public ResponseEntity<Map<String, Object>> updateGameScoreData(@RequestBody @Valid GameScoreDataUpdateDTO gameScoreDataUpdateDTO) {
        GameScoreData existingGameScoreData = getEntityById(gameScoreDataUpdateDTO.getId())
                .orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,
                        "GameScoreData with id " + gameScoreDataUpdateDTO.getId() + " not found"));
        GameScoreData updatedGameScoreData = fromGameScoreDataUpdateDTO(gameScoreDataUpdateDTO, existingGameScoreData);
        updateEntity(updatedGameScoreData.getId(), updatedGameScoreData);
        processGameScoreData(updatedGameScoreData);
        logger.info("GameScoreData updated with id {}", updatedGameScoreData.getId());
        return ResponseEntity.ok(Map.of("id", updatedGameScoreData.getId(), "status", "processed"));
    }

    @DeleteMapping
    public ResponseEntity<Map<String, String>> deleteGameScoreData(@RequestParam @NotBlank String id) {
        if (deleteEntity(id)) {
            logger.info("GameScoreData deleted with id {}", id);
            return ResponseEntity.ok(Map.of("id", id, "status", "deleted"));
        } else {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,
                    "GameScoreData with id " + id + " not found");
        }
    }

    private String addGameScoreData(GameScoreData gameScoreData) {
        String id = String.valueOf(gameScoreDataIdCounter.getAndIncrement());
        gameScoreData.setId(id);
        if (gameScoreData.getTechnicalId() == null) gameScoreData.setTechnicalId(UUID.randomUUID());
        gameScoreDataCache.computeIfAbsent("entities", k -> Collections.synchronizedList(new ArrayList<>())).add(gameScoreData);
        processGameScoreData(gameScoreData);
        return id;
    }

    private Optional<GameScoreData> getEntityById(String id) {
        List<GameScoreData> entities = gameScoreDataCache.get("entities");
        if (entities == null) return Optional.empty();

        synchronized (entities) {
            return entities.stream()
                    .filter(entity -> id.equals(entity.getId()))
                    .findFirst();
        }
    }

    private void updateEntity(String id, GameScoreData updatedEntity) {
        List<GameScoreData> entities = gameScoreDataCache.get("entities");
        if (entities == null) throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,
                "GameScoreData with id " + id + " not found");
        synchronized (entities) {
            for (int i = 0; i < entities.size(); i++) {
                GameScoreData entity = entities.get(i);
                if (id.equals(entity.getId())) {
                    updatedEntity.setId(id);
                    updatedEntity.setTechnicalId(entity.getTechnicalId());
                    entities.set(i, updatedEntity);
                    return;
                }
            }
        }
        throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,
                "GameScoreData with id " + id + " not found");
    }

    private boolean deleteEntity(String id) {
        List<GameScoreData> entities = gameScoreDataCache.get("entities");
        if (entities == null) return false;
        synchronized (entities) {
            Iterator<GameScoreData> iterator = entities.iterator();
            while (iterator.hasNext()) {
                GameScoreData entity = iterator.next();
                if (id.equals(entity.getId())) {
                    iterator.remove();
                    return true;
                }
            }
        }
        return false;
    }

    private void processGameScoreData(GameScoreData gameScoreData) {
        logger.info("Processing GameScoreData event: id={} date={}", gameScoreData.getId(), gameScoreData.getDate());
        // TODO: Add business logic for GameScoreData event processing simulation
    }

    @Data
    public static class GameScoreDataDTO {
        @NotBlank
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "Date must be in yyyy-MM-dd format")
        private String date;

        @NotNull
        private Integer homeScore;

        @NotNull
        private Integer awayScore;

        @NotBlank
        private String homeTeam;

        @NotBlank
        private String awayTeam;
    }

    @Data
    public static class GameScoreDataUpdateDTO {
        @NotBlank
        private String id;

        @NotBlank
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "Date must be in yyyy-MM-dd format")
        private String date;

        @NotNull
        private Integer homeScore;

        @NotNull
        private Integer awayScore;

        @NotBlank
        private String homeTeam;

        @NotBlank
        private String awayTeam;
    }

    private GameScoreData fromGameScoreDataDTO(GameScoreDataDTO dto) {
        GameScoreData gsd = new GameScoreData();
        gsd.setDate(dto.getDate());
        gsd.setHomeScore(dto.getHomeScore());
        gsd.setAwayScore(dto.getAwayScore());
        gsd.setHomeTeam(dto.getHomeTeam());
        gsd.setAwayTeam(dto.getAwayTeam());
        return gsd;
    }

    private GameScoreData fromGameScoreDataUpdateDTO(GameScoreDataUpdateDTO dto, GameScoreData existing) {
        existing.setDate(dto.getDate());
        existing.setHomeScore(dto.getHomeScore());
        existing.setAwayScore(dto.getAwayScore());
        existing.setHomeTeam(dto.getHomeTeam());
        existing.setAwayTeam(dto.getAwayTeam());
        return existing;
    }
}