package com.java_template.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.DigestData;
import com.java_template.application.entity.DigestEmail;
import com.java_template.application.entity.DigestRequestJob;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping(path = "/entity")
public class Controller {

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$",
            Pattern.CASE_INSENSITIVE);

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/digestRequestJob")
    public ResponseEntity<?> createDigestRequestJob(@Valid @RequestBody DigestRequestJob requestJob) throws JsonProcessingException, ExecutionException, InterruptedException {
        if (requestJob == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Request body is missing");
        }
        if (requestJob.getEmail() == null || requestJob.getEmail().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Email is required");
        }
        if (!EMAIL_PATTERN.matcher(requestJob.getEmail()).matches()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid email format");
        }
        if (requestJob.getMetadata() == null || requestJob.getMetadata().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Metadata is required");
        }

        requestJob.setStatus("PENDING");
        requestJob.setCreatedAt(Instant.now());

        CompletableFuture<UUID> idFuture = entityService.addItem("digestRequestJob", ENTITY_VERSION, requestJob);
        UUID technicalId = idFuture.get();

        Map<String, String> response = new HashMap<>();
        response.put("technicalId", technicalId.toString());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/digestRequestJob/{id}")
    public ResponseEntity<?> getDigestRequestJob(@PathVariable("id") String idStr) throws JsonProcessingException, ExecutionException, InterruptedException {
        UUID id = UUID.fromString(idStr);
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem("digestRequestJob", ENTITY_VERSION, id);
        ObjectNode node = itemFuture.get();
        if (node == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("DigestRequestJob not found");
        }
        DigestRequestJob job = objectMapper.treeToValue(node, DigestRequestJob.class);
        return ResponseEntity.ok(job);
    }

    @GetMapping("/digestData/{id}")
    public ResponseEntity<?> getDigestData(@PathVariable("id") String idStr) throws JsonProcessingException, ExecutionException, InterruptedException {
        UUID id = UUID.fromString(idStr);
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem("digestData", ENTITY_VERSION, id);
        ObjectNode node = itemFuture.get();
        if (node == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("DigestData not found");
        }
        DigestData data = objectMapper.treeToValue(node, DigestData.class);
        return ResponseEntity.ok(data);
    }

    @GetMapping("/digestEmail/{id}")
    public ResponseEntity<?> getDigestEmail(@PathVariable("id") String idStr) throws JsonProcessingException, ExecutionException, InterruptedException {
        UUID id = UUID.fromString(idStr);
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem("digestEmail", ENTITY_VERSION, id);
        ObjectNode node = itemFuture.get();
        if (node == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("DigestEmail not found");
        }
        DigestEmail email = objectMapper.treeToValue(node, DigestEmail.class);
        return ResponseEntity.ok(email);
    }

}