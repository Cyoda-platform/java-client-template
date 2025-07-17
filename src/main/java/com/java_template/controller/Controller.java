package com.java_template.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.DigestData;
import com.java_template.application.entity.DigestRequest;
import com.java_template.application.entity.EmailDispatchLog;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/cyoda-digest")
@Validated
@RequiredArgsConstructor
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    private final RestTemplate restTemplate = new RestTemplate();

    @PostMapping("/digest-requests")
    public CreateDigestResponse createOrUpdateDigestRequest(@RequestBody @Valid DigestRequestInput input) throws ExecutionException, InterruptedException {
        logger.info("Received DigestRequest create/update request for email={}", input.getEmail());

        DigestRequest req = new DigestRequest();
        req.setEmail(input.getEmail());
        req.setMetadata(Optional.ofNullable(input.getMetadata()).orElse(new HashMap<>()));
        req.setRequestedEndpoint(Optional.ofNullable(input.getRequestedEndpoint()).filter(s -> !s.isBlank()).orElse("/pet/findByStatus"));
        req.setRequestedParameters(Optional.ofNullable(input.getRequestedParameters()).orElse(Map.of("status", "available")));
        req.setDigestFormat(Optional.ofNullable(input.getDigestFormat()).orElse(DigestRequest.DigestFormat.HTML));
        req.setStatus(DigestRequest.Status.RECEIVED);
        Instant now = Instant.now();
        req.setCreatedAt(now);
        req.setUpdatedAt(now);

        CompletableFuture<UUID> idFuture = entityService.addItem("DigestRequest", ENTITY_VERSION, req);
        UUID technicalId = idFuture.get();
        logger.info("Saved DigestRequest technicalId={} to external service", technicalId);

        req.setTechnicalId(technicalId);

        // Business logic for processing the request has been moved to processors, so no processing here

        return new CreateDigestResponse(technicalId.toString(), req.getStatus().toString());
    }

    @GetMapping("/digest-requests/{id}")
    public DigestRequest getDigestRequest(@PathVariable @NotBlank String id) throws ExecutionException, InterruptedException {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format for DigestRequest id={}", id);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid DigestRequest id format");
        }

        CompletableFuture<ObjectNode> itemFuture = entityService.getItem("DigestRequest", ENTITY_VERSION, technicalId);
        ObjectNode node = itemFuture.get();
        if (node == null || node.isEmpty()) {
            logger.error("DigestRequest technicalId={} not found", technicalId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "DigestRequest not found");
        }
        DigestRequest req = JsonNodeToDigestRequest(node);
        return req;
    }

    @GetMapping("/digest-requests/{id}/digest-data")
    public DigestDataResponse getDigestData(@PathVariable @NotBlank String id) throws ExecutionException, InterruptedException {
        UUID digestRequestId;
        try {
            digestRequestId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format for DigestRequest id={}", id);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid DigestRequest id format");
        }

        Condition cond = Condition.of("$.digestRequestId", "EQUALS", digestRequestId.toString());
        SearchConditionRequest condition = SearchConditionRequest.group("AND", cond);

        CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition("DigestData", ENTITY_VERSION, condition);
        ArrayNode items = filteredItemsFuture.get();

        if (items == null || items.isEmpty()) {
            logger.error("DigestData for DigestRequest technicalId={} not found", digestRequestId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "DigestData not found");
        }

        // take first matching DigestData
        ObjectNode dataNode = (ObjectNode) items.get(0);
        DigestData digestData = JsonNodeToDigestData(dataNode);

        return new DigestDataResponse(digestData.getRetrievedData());
    }

    private DigestRequest JsonNodeToDigestRequest(ObjectNode node) {
        DigestRequest req = new DigestRequest();
        if (node.has("technicalId")) {
            req.setTechnicalId(UUID.fromString(node.get("technicalId").asText()));
        }
        if (node.has("email")) {
            req.setEmail(node.get("email").asText());
        }
        if (node.has("metadata") && !node.get("metadata").isNull()) {
            Map<String, String> metadata = new HashMap<>();
            node.get("metadata").fields().forEachRemaining(e -> metadata.put(e.getKey(), e.getValue().asText()));
            req.setMetadata(metadata);
        }
        if (node.has("requestedEndpoint")) {
            req.setRequestedEndpoint(node.get("requestedEndpoint").asText());
        }
        if (node.has("requestedParameters") && !node.get("requestedParameters").isNull()) {
            Map<String, String> params = new HashMap<>();
            node.get("requestedParameters").fields().forEachRemaining(e -> params.put(e.getKey(), e.getValue().asText()));
            req.setRequestedParameters(params);
        }
        if (node.has("digestFormat")) {
            try {
                req.setDigestFormat(DigestRequest.DigestFormat.valueOf(node.get("digestFormat").asText()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (node.has("status")) {
            try {
                req.setStatus(DigestRequest.Status.valueOf(node.get("status").asText()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (node.has("createdAt")) {
            req.setCreatedAt(Instant.parse(node.get("createdAt").asText()));
        }
        if (node.has("updatedAt")) {
            req.setUpdatedAt(Instant.parse(node.get("updatedAt").asText()));
        }
        return req;
    }

    private DigestData JsonNodeToDigestData(ObjectNode node) {
        DigestData digestData = new DigestData();
        if (node.has("technicalId")) {
            digestData.setTechnicalId(UUID.fromString(node.get("technicalId").asText()));
        }
        if (node.has("digestRequestId")) {
            digestData.setDigestRequestId(node.get("digestRequestId").asText());
        }
        if (node.has("retrievedData")) {
            digestData.setRetrievedData(node.get("retrievedData").asText());
        }
        if (node.has("createdAt")) {
            digestData.setCreatedAt(Instant.parse(node.get("createdAt").asText()));
        }
        return digestData;
    }

    @Data
    public static class DigestRequestInput {
        @NotBlank
        @Email
        private String email;
        private Map<String, String> metadata;
        private String requestedEndpoint;
        private Map<String, String> requestedParameters;
        private DigestRequest.DigestFormat digestFormat;
    }

    @Data
    public static class CreateDigestResponse {
        @NotNull
        private final String id;
        @NotNull
        private final String status;
    }

    @Data
    public static class DigestDataResponse {
        @NotNull
        private final String retrievedData;
    }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, Object> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", ex.getStatusCode().toString());
        error.put("message", ex.getReason());
        return error;
    }
}