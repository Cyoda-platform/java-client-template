package com.java_template.prototype;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.DigestContent;
import com.java_template.application.entity.DigestJob;
import com.java_template.application.entity.DigestRequest;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping(path = "/digestjob")
public class DigestJobController {

    private static final Logger logger = LoggerFactory.getLogger(DigestJobController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int ENTITY_VERSION = ENTITY_VERSION;
    private static final String ENTITY_NAME = "DigestJob";

    private final AtomicLong digestJobIdCounter = new AtomicLong(1);

    public DigestJobController(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createDigestJob(@RequestBody @Valid DigestJobRequest digestJobReq) throws ExecutionException, InterruptedException, JsonProcessingException {
        logger.info("Received request to create DigestJob: {}", digestJobReq);

        DigestJob digestJob = new DigestJob();
        digestJob.setId(String.valueOf(digestJobIdCounter.getAndIncrement()));
        digestJob.setTechnicalId(UUID.randomUUID());
        // Set required fields to valid values or from digestJobReq if available
        digestJob.setUserEmail(""); // TODO: Set proper userEmail
        digestJob.setStatus("PENDING");
        digestJob.setCreatedAt(java.time.Instant.now());
        digestJob.setUpdatedAt(java.time.Instant.now());

        if (!digestJob.isValid()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid DigestJob entity");
        }

        CompletableFuture<UUID> idFuture = entityService.addItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                digestJob);

        UUID technicalId = idFuture.get();

        processDigestJob(digestJob);

        logger.info("DigestJob created with id {}", digestJob.getId());
        return ResponseEntity.ok(Map.of("id", digestJob.getId(), "status", "processed"));
    }

    @GetMapping
    public ResponseEntity<DigestJob> getDigestJob(@RequestParam @NotBlank String id) throws ExecutionException, InterruptedException, JsonProcessingException {
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.id", "EQUALS", id));
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                ENTITY_NAME,
                ENTITY_VERSION,
                condition);
        ArrayNode nodes = itemsFuture.get();

        if (nodes == null || nodes.size() == 0) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "DigestJob not found");
        }

        ObjectNode node = (ObjectNode) nodes.get(0);
        DigestJob job = objectMapper.treeToValue(node, DigestJob.class);
        return ResponseEntity.ok(job);
    }

    @PutMapping
    public ResponseEntity<Map<String, Object>> updateDigestJob(@RequestBody @Valid DigestJobUpdateRequest digestJobUpdateReq) throws ExecutionException, InterruptedException, JsonProcessingException {
        logger.info("Received request to update DigestJob id {}: {}", digestJobUpdateReq.getId(), digestJobUpdateReq);

        DigestJob existing = getDigestJobById(digestJobUpdateReq.getId());

        DigestJob updated = new DigestJob();
        updated.setId(existing.getId());
        updated.setTechnicalId(existing.getTechnicalId());
        // Set other fields to update from digestJobUpdateReq as needed
        updated.setUserEmail(existing.getUserEmail());
        updated.setStatus(existing.getStatus());
        updated.setCreatedAt(existing.getCreatedAt());
        updated.setUpdatedAt(java.time.Instant.now());

        if (!updated.isValid()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid DigestJob entity");
        }

        CompletableFuture<UUID> updatedItemId = entityService.updateItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                existing.getTechnicalId(),
                updated);

        updatedItemId.get();

        processDigestJob(updated);

        logger.info("DigestJob updated with id {}", updated.getId());
        return ResponseEntity.ok(Map.of("id", updated.getId(), "status", "updated and processed"));
    }

    @DeleteMapping
    public ResponseEntity<Map<String, String>> deleteDigestJob(@RequestParam @NotBlank String id) throws ExecutionException, InterruptedException {
        logger.info("Received request to delete DigestJob id {}", id);

        DigestJob existing = getDigestJobById(id);

        CompletableFuture<UUID> deletedItemId = entityService.deleteItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                existing.getTechnicalId());

        deletedItemId.get();

        logger.info("DigestJob deleted with id {}", id);
        return ResponseEntity.ok(Map.of("id", id, "status", "deleted"));
    }

    private DigestJob getDigestJobById(String id) throws ExecutionException, InterruptedException, JsonProcessingException {
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.id", "EQUALS", id));
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                ENTITY_NAME,
                ENTITY_VERSION,
                condition);
        ArrayNode nodes = itemsFuture.get();

        if (nodes == null || nodes.size() == 0) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "DigestJob not found");
        }

        ObjectNode node = (ObjectNode) nodes.get(0);
        return objectMapper.treeToValue(node, DigestJob.class);
    }

    private void processDigestJob(DigestJob job) throws ExecutionException, InterruptedException, JsonProcessingException {
        logger.info("Processing DigestJob event for id {}", job.getId());

        // Retrieve all DigestRequest entities
        CompletableFuture<ArrayNode> requestsFuture = entityService.getItems("DigestRequest", ENTITY_VERSION);
        ArrayNode requestsNodes = requestsFuture.get();

        if (requestsNodes == null || requestsNodes.size() == 0) {
            logger.info("No DigestRequests found to associate with DigestJob id {}", job.getId());
        } else {
            logger.info("Found {} DigestRequests, processing them for DigestJob id {}", requestsNodes.size(), job.getId());
            for (int i = 0; i < requestsNodes.size(); i++) {
                ObjectNode reqNode = (ObjectNode) requestsNodes.get(i);
                DigestRequest req = objectMapper.treeToValue(reqNode, DigestRequest.class);

                DigestContent content = new DigestContent();
                content.setId(null);
                content.setTechnicalId(UUID.randomUUID());
                content.setRequestId(req.getId());
                content.setDigestJobId(job.getId());
                content.setContent("Generated content for request " + req.getId());
                content.setFormat("PLAIN_TEXT");
                content.setCreatedAt(java.time.Instant.now());

                // Add DigestContent using entityService
                CompletableFuture<UUID> addedContentIdFuture = entityService.addItem(
                        "DigestContent",
                        ENTITY_VERSION,
                        content);
                addedContentIdFuture.get();

                // Process the DigestContent
                processDigestContent(content);
            }
        }

        logger.info("DigestJob event processing completed for id {}", job.getId());
    }

    private void processDigestContent(DigestContent content) {
        logger.info("Processing DigestContent event for id {}", content.getId());

        if (content.getContent() == null || content.getContent().isBlank()) {
            logger.warn("DigestContent id {} has empty content field", content.getId());
        } else {
            logger.info("DigestContent id {} content length: {}", content.getId(), content.getContent().length());
        }
        // No external call here to keep consistent with original logic
    }

    @Data
    public static class DigestJobRequest {
        // No fields specified for DigestJob in requirements - add if needed
    }

    @Data
    public static class DigestJobUpdateRequest {
        @NotBlank
        private String id;
        // Add other updatable fields as needed
    }
}

@Slf4j
@Validated
@RestController
@RequestMapping(path = "/digestrequest")
class DigestRequestController {

    private static final Logger logger = LoggerFactory.getLogger(DigestRequestController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int ENTITY_VERSION = ENTITY_VERSION;
    private static final String ENTITY_NAME = "DigestRequest";

    private final AtomicLong digestRequestIdCounter = new AtomicLong(1);

    public DigestRequestController(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createDigestRequest(@RequestBody @Valid DigestRequestRequest digestRequestReq) throws ExecutionException, InterruptedException, JsonProcessingException {
        logger.info("Received request to create DigestRequest: {}", digestRequestReq);

        DigestRequest request = new DigestRequest();
        request.setId(String.valueOf(digestRequestIdCounter.getAndIncrement()));
        request.setTechnicalId(UUID.randomUUID());
        // Set required fields or from digestRequestReq if available
        request.setUserEmail(""); // TODO: Set proper userEmail
        request.setReceivedAt(java.time.Instant.now());

        if (!request.isValid()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid DigestRequest entity");
        }

        CompletableFuture<UUID> idFuture = entityService.addItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                request);

        UUID technicalId = idFuture.get();

        processDigestRequest(request);

        logger.info("DigestRequest created with id {}", request.getId());
        return ResponseEntity.ok(Map.of("id", request.getId(), "status", "processed"));
    }

    @GetMapping
    public ResponseEntity<DigestRequest> getDigestRequest(@RequestParam @NotBlank String id) throws ExecutionException, InterruptedException, JsonProcessingException {
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.id", "EQUALS", id));
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                ENTITY_NAME,
                ENTITY_VERSION,
                condition);
        ArrayNode nodes = itemsFuture.get();

        if (nodes == null || nodes.size() == 0) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "DigestRequest not found");
        }

        ObjectNode node = (ObjectNode) nodes.get(0);
        DigestRequest req = objectMapper.treeToValue(node, DigestRequest.class);
        return ResponseEntity.ok(req);
    }

    @PutMapping
    public ResponseEntity<Map<String, Object>> updateDigestRequest(@RequestBody @Valid DigestRequestUpdateRequest digestRequestUpdateReq) throws ExecutionException, InterruptedException, JsonProcessingException {
        logger.info("Received request to update DigestRequest id {}: {}", digestRequestUpdateReq.getId(), digestRequestUpdateReq);

        DigestRequest existing = getDigestRequestById(digestRequestUpdateReq.getId());

        DigestRequest updated = new DigestRequest();
        updated.setId(existing.getId());
        updated.setTechnicalId(existing.getTechnicalId());
        // Set other fields to update from digestRequestUpdateReq as needed
        updated.setUserEmail(existing.getUserEmail());
        updated.setReceivedAt(existing.getReceivedAt());

        if (!updated.isValid()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid DigestRequest entity");
        }

        CompletableFuture<UUID> updatedItemId = entityService.updateItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                existing.getTechnicalId(),
                updated);

        updatedItemId.get();

        processDigestRequest(updated);

        logger.info("DigestRequest updated with id {}", updated.getId());
        return ResponseEntity.ok(Map.of("id", updated.getId(), "status", "updated and processed"));
    }

    @DeleteMapping
    public ResponseEntity<Map<String, String>> deleteDigestRequest(@RequestParam @NotBlank String id) throws ExecutionException, InterruptedException {
        logger.info("Received request to delete DigestRequest id {}", id);

        DigestRequest existing = getDigestRequestById(id);

        CompletableFuture<UUID> deletedItemId = entityService.deleteItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                existing.getTechnicalId());

        deletedItemId.get();

        logger.info("DigestRequest deleted with id {}", id);
        return ResponseEntity.ok(Map.of("id", id, "status", "deleted"));
    }

    private DigestRequest getDigestRequestById(String id) throws ExecutionException, InterruptedException, JsonProcessingException {
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.id", "EQUALS", id));
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                ENTITY_NAME,
                ENTITY_VERSION,
                condition);
        ArrayNode nodes = itemsFuture.get();

        if (nodes == null || nodes.size() == 0) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "DigestRequest not found");
        }

        ObjectNode node = (ObjectNode) nodes.get(0);
        return objectMapper.treeToValue(node, DigestRequest.class);
    }

    private void processDigestRequest(DigestRequest request) throws ExecutionException, InterruptedException {
        logger.info("Processing DigestRequest event for id {}", request.getId());

        // Check linked DigestJob existence
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.id", "EQUALS", request.getId()));

        CompletableFuture<ArrayNode> jobsFuture = entityService.getItemsByCondition(
                "DigestJob",
                ENTITY_VERSION,
                condition);
        ArrayNode jobsNodes = jobsFuture.get();

        if (jobsNodes == null || jobsNodes.size() == 0) {
            logger.info("No linked DigestJob found for DigestRequest id {}", request.getId());
        } else {
            logger.info("Linked DigestJob found for DigestRequest id {}", request.getId());
        }
    }

    @Data
    public static class DigestRequestRequest {
        // No fields specified; add if needed
    }

    @Data
    public static class DigestRequestUpdateRequest {
        @NotBlank
        private String id;
        // Add other updatable fields as needed
    }
}

@Slf4j
@Validated
@RestController
@RequestMapping(path = "/digestcontent")
class DigestContentController {

    private static final Logger logger = LoggerFactory.getLogger(DigestContentController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int ENTITY_VERSION = ENTITY_VERSION;
    private static final String ENTITY_NAME = "DigestContent";

    private final AtomicLong digestContentIdCounter = new AtomicLong(1);

    public DigestContentController(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createDigestContent(@RequestBody @Valid DigestContentRequest digestContentReq) throws ExecutionException, InterruptedException, JsonProcessingException {
        logger.info("Received request to create DigestContent: {}", digestContentReq);

        DigestContent content = new DigestContent();
        content.setId(String.valueOf(digestContentIdCounter.getAndIncrement()));
        content.setTechnicalId(UUID.randomUUID());
        content.setContent(digestContentReq.getContent());
        content.setRequestId(digestContentReq.getRequestId());
        content.setDigestJobId(digestContentReq.getDigestJobId());
        content.setFormat("PLAIN_TEXT");
        content.setCreatedAt(java.time.Instant.now());

        if (!content.isValid()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid DigestContent entity");
        }

        CompletableFuture<UUID> idFuture = entityService.addItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                content);

        UUID technicalId = idFuture.get();

        processDigestContent(content);

        logger.info("DigestContent created with id {}", content.getId());
        return ResponseEntity.ok(Map.of("id", content.getId(), "status", "processed"));
    }

    @GetMapping
    public ResponseEntity<DigestContent> getDigestContent(@RequestParam @NotBlank String id) throws ExecutionException, InterruptedException, JsonProcessingException {
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.id", "EQUALS", id));
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                ENTITY_NAME,
                ENTITY_VERSION,
                condition);
        ArrayNode nodes = itemsFuture.get();

        if (nodes == null || nodes.size() == 0) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "DigestContent not found");
        }

        ObjectNode node = (ObjectNode) nodes.get(0);
        DigestContent content = objectMapper.treeToValue(node, DigestContent.class);
        return ResponseEntity.ok(content);
    }

    @PutMapping
    public ResponseEntity<Map<String, Object>> updateDigestContent(@RequestBody @Valid DigestContentUpdateRequest digestContentUpdateReq) throws ExecutionException, InterruptedException, JsonProcessingException {
        logger.info("Received request to update DigestContent id {}: {}", digestContentUpdateReq.getId(), digestContentUpdateReq);

        DigestContent existing = getDigestContentById(digestContentUpdateReq.getId());

        DigestContent updated = new DigestContent();
        updated.setId(existing.getId());
        updated.setTechnicalId(existing.getTechnicalId());
        updated.setContent(digestContentUpdateReq.getContent());
        updated.setRequestId(digestContentUpdateReq.getRequestId());
        updated.setDigestJobId(digestContentUpdateReq.getDigestJobId());
        updated.setFormat("PLAIN_TEXT");
        updated.setCreatedAt(existing.getCreatedAt());

        if (!updated.isValid()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid DigestContent entity");
        }

        CompletableFuture<UUID> updatedItemId = entityService.updateItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                existing.getTechnicalId(),
                updated);

        updatedItemId.get();

        processDigestContent(updated);

        logger.info("DigestContent updated with id {}", updated.getId());
        return ResponseEntity.ok(Map.of("id", updated.getId(), "status", "updated and processed"));
    }

    @DeleteMapping
    public ResponseEntity<Map<String, String>> deleteDigestContent(@RequestParam @NotBlank String id) throws ExecutionException, InterruptedException {
        logger.info("Received request to delete DigestContent id {}", id);

        DigestContent existing = getDigestContentById(id);

        CompletableFuture<UUID> deletedItemId = entityService.deleteItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                existing.getTechnicalId());

        deletedItemId.get();

        logger.info("DigestContent deleted with id {}", id);
        return ResponseEntity.ok(Map.of("id", id, "status", "deleted"));
    }

    private DigestContent getDigestContentById(String id) throws ExecutionException, InterruptedException, JsonProcessingException {
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.id", "EQUALS", id));
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                ENTITY_NAME,
                ENTITY_VERSION,
                condition);
        ArrayNode nodes = itemsFuture.get();

        if (nodes == null || nodes.size() == 0) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "DigestContent not found");
        }

        ObjectNode node = (ObjectNode) nodes.get(0);
        return objectMapper.treeToValue(node, DigestContent.class);
    }

    private void processDigestContent(DigestContent content) throws ExecutionException, InterruptedException {
        logger.info("Processing DigestContent event for id {}", content.getId());

        if (content.getContent() == null || content.getContent().isBlank()) {
            logger.warn("DigestContent id {} has empty content field", content.getId());
        } else {
            logger.info("DigestContent id {} content length: {}", content.getId(), content.getContent().length());
        }

        try {
            DigestRequest linkedRequest = getDigestRequestById(content.getRequestId());
            logger.info("Linked DigestRequest found for DigestContent id {}: requestId {}", content.getId(), linkedRequest.getId());
        } catch (ResponseStatusException ex) {
            logger.warn("Linked DigestRequest id {} not found for DigestContent id {}", content.getRequestId(), content.getId());
        }
    }

    private DigestRequest getDigestRequestById(String id) throws ExecutionException, InterruptedException, JsonProcessingException {
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.id", "EQUALS", id));
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                "DigestRequest",
                ENTITY_VERSION,
                condition);
        ArrayNode nodes = itemsFuture.get();

        if (nodes == null || nodes.size() == 0) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "DigestRequest not found");
        }

        ObjectNode node = (ObjectNode) nodes.get(0);
        return objectMapper.treeToValue(node, DigestRequest.class);
    }

    @Data
    public static class DigestContentRequest {
        @NotBlank
        private String digestJobId;

        @NotBlank
        private String requestId;

        @NotNull
        @Size(min = 1, max = 10000)
        private String content;
    }

    @Data
    public static class DigestContentUpdateRequest {
        @NotBlank
        private String id;

        @NotBlank
        private String digestJobId;

        @NotBlank
        private String requestId;

        @NotNull
        @Size(min = 1, max = 10000)
        private String content;
    }
} 

// Utility class for JSON conversion (may be part of project utils)
class JsonUtil {
    private static final ObjectMapper mapper = new ObjectMapper();

    static <T> T convert(ObjectNode node, Class<T> clazz) {
        try {
            return mapper.treeToValue(node, clazz);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to convert ObjectNode to " + clazz.getSimpleName(), e);
        }
    }
}