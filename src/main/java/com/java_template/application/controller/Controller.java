package com.java_template.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.DigestDataRecord;
import com.java_template.application.entity.DigestEmailRecord;
import com.java_template.application.entity.DigestRequestJob;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping(path = "/controller")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private final EntityService entityService;

    private final ObjectMapper objectMapper;

    // POST /controller/digestRequestJob - Create new DigestRequestJob
    @PostMapping("/digestRequestJob")
    public ResponseEntity<?> createDigestRequestJob(@RequestBody DigestRequestJob request) throws ExecutionException, InterruptedException {
        try {
            if (request.getUserEmail() == null || request.getUserEmail().isBlank()) {
                log.error("Invalid userEmail");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("userEmail is required and cannot be blank");
            }
            if (request.getEventMetadata() == null || request.getEventMetadata().isBlank()) {
                log.error("Invalid eventMetadata");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("eventMetadata is required and cannot be blank");
            }

            // Prepare data to add via entityService
            DigestRequestJob job = new DigestRequestJob();
            job.setUserEmail(request.getUserEmail());
            job.setEventMetadata(request.getEventMetadata());
            job.setStatus("PENDING");
            job.setCreatedAt(java.time.Instant.now().toString());

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    "DigestRequestJob",
                    ENTITY_VERSION,
                    job);

            UUID technicalUUID = idFuture.get(); // wait for completion
            String technicalId = technicalUUID.toString();

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            log.error("Bad request in createDigestRequestJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            log.error("Internal error in createDigestRequestJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    // GET /controller/digestRequestJob/{id} - Retrieve DigestRequestJob by technicalId
    @GetMapping("/digestRequestJob/{id}")
    public ResponseEntity<?> getDigestRequestJob(@PathVariable("id") String technicalId) throws ExecutionException, InterruptedException, JsonProcessingException {
        try {
            UUID uuid = UUID.fromString(technicalId);
            ObjectNode node = entityService.getItem("DigestRequestJob", ENTITY_VERSION, uuid).get();
            if (node == null) {
                log.error("DigestRequestJob not found for id {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("DigestRequestJob not found");
            }
            DigestRequestJob job = objectMapper.treeToValue(node, DigestRequestJob.class);
            return ResponseEntity.ok(job);
        } catch (IllegalArgumentException e) {
            log.error("Bad request in getDigestRequestJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            if (e.getCause() instanceof java.util.NoSuchElementException) {
                log.error("DigestRequestJob not found for id {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("DigestRequestJob not found");
            }
            throw e;
        } catch (Exception e) {
            log.error("Internal error in getDigestRequestJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    // GET /controller/digestDataRecord/{id} - Retrieve DigestDataRecord by technicalId
    @GetMapping("/digestDataRecord/{id}")
    public ResponseEntity<?> getDigestDataRecord(@PathVariable("id") String technicalId) throws ExecutionException, InterruptedException, JsonProcessingException {
        try {
            UUID uuid = UUID.fromString(technicalId);
            ObjectNode node = entityService.getItem("DigestDataRecord", ENTITY_VERSION, uuid).get();
            if (node == null) {
                log.error("DigestDataRecord not found for id {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("DigestDataRecord not found");
            }
            DigestDataRecord record = objectMapper.treeToValue(node, DigestDataRecord.class);
            return ResponseEntity.ok(record);
        } catch (IllegalArgumentException e) {
            log.error("Bad request in getDigestDataRecord: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            if (e.getCause() instanceof java.util.NoSuchElementException) {
                log.error("DigestDataRecord not found for id {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("DigestDataRecord not found");
            }
            throw e;
        } catch (Exception e) {
            log.error("Internal error in getDigestDataRecord: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    // GET /controller/digestEmailRecord/{id} - Retrieve DigestEmailRecord by technicalId
    @GetMapping("/digestEmailRecord/{id}")
    public ResponseEntity<?> getDigestEmailRecord(@PathVariable("id") String technicalId) throws ExecutionException, InterruptedException, JsonProcessingException {
        try {
            UUID uuid = UUID.fromString(technicalId);
            ObjectNode node = entityService.getItem("DigestEmailRecord", ENTITY_VERSION, uuid).get();
            if (node == null) {
                log.error("DigestEmailRecord not found for id {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("DigestEmailRecord not found");
            }
            DigestEmailRecord record = objectMapper.treeToValue(node, DigestEmailRecord.class);
            return ResponseEntity.ok(record);
        } catch (IllegalArgumentException e) {
            log.error("Bad request in getDigestEmailRecord: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            if (e.getCause() instanceof java.util.NoSuchElementException) {
                log.error("DigestEmailRecord not found for id {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("DigestEmailRecord not found");
            }
            throw e;
        } catch (Exception e) {
            log.error("Internal error in getDigestEmailRecord: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }
}