package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.DigestData;
import com.java_template.application.entity.DigestEmail;
import com.java_template.application.entity.DigestRequestJob;
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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
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

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$",
            Pattern.CASE_INSENSITIVE);

    // We keep local counters only for generating technicalIds prefixes (for response)
    // Actual data is stored remotely in EntityService with UUID keys
    private final AtomicLong digestRequestJobCounter = new AtomicLong(1);
    private final AtomicLong digestDataCounter = new AtomicLong(1);
    private final AtomicLong digestEmailCounter = new AtomicLong(1);

    @PostMapping("/digestRequestJob")
    public ResponseEntity<?> createDigestRequestJob(@RequestBody DigestRequestJob requestJob) {
        try {
            if (requestJob == null) {
                logger.error("Received null DigestRequestJob");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Request body is missing");
            }
            if (requestJob.getEmail() == null || requestJob.getEmail().isBlank()) {
                logger.error("Email is blank");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Email is required");
            }
            if (!EMAIL_PATTERN.matcher(requestJob.getEmail()).matches()) {
                logger.error("Invalid email format: {}", requestJob.getEmail());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid email format");
            }
            if (requestJob.getMetadata() == null || requestJob.getMetadata().isBlank()) {
                logger.error("Metadata is blank");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Metadata is required");
            }

            requestJob.setStatus("PENDING");
            requestJob.setCreatedAt(Instant.now());

            CompletableFuture<UUID> idFuture = entityService.addItem("digestRequestJob", ENTITY_VERSION, requestJob);
            UUID technicalId = idFuture.get(); // blocking wait here for simplicity

            logger.info("DigestRequestJob created with technicalId: {}", technicalId);

            // processDigestRequestJob(technicalId, requestJob); // removed processing method call

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId.toString());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            logger.error("Illegal argument: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            logger.error("Failed to create DigestRequestJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    @GetMapping("/digestRequestJob/{id}")
    public ResponseEntity<?> getDigestRequestJob(@PathVariable("id") String idStr) {
        try {
            UUID id = UUID.fromString(idStr);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem("digestRequestJob", ENTITY_VERSION, id);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                logger.error("DigestRequestJob not found for id: {}", idStr);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("DigestRequestJob not found");
            }
            DigestRequestJob job = objectMapper.treeToValue(node, DigestRequestJob.class);
            return ResponseEntity.ok(job);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format: {}", idStr);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid id format");
        } catch (Exception e) {
            logger.error("Failed to get DigestRequestJob with id: {}", idStr, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    @GetMapping("/digestData/{id}")
    public ResponseEntity<?> getDigestData(@PathVariable("id") String idStr) {
        try {
            UUID id = UUID.fromString(idStr);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem("digestData", ENTITY_VERSION, id);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                logger.error("DigestData not found for id: {}", idStr);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("DigestData not found");
            }
            DigestData data = objectMapper.treeToValue(node, DigestData.class);
            return ResponseEntity.ok(data);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format: {}", idStr);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid id format");
        } catch (Exception e) {
            logger.error("Failed to get DigestData with id: {}", idStr, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

    @GetMapping("/digestEmail/{id}")
    public ResponseEntity<?> getDigestEmail(@PathVariable("id") String idStr) {
        try {
            UUID id = UUID.fromString(idStr);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem("digestEmail", ENTITY_VERSION, id);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                logger.error("DigestEmail not found for id: {}", idStr);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("DigestEmail not found");
            }
            DigestEmail email = objectMapper.treeToValue(node, DigestEmail.class);
            return ResponseEntity.ok(email);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format: {}", idStr);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid id format");
        } catch (Exception e) {
            logger.error("Failed to get DigestEmail with id: {}", idStr, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }

}