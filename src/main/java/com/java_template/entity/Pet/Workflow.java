package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static com.java_template.common.config.Config.*;

@Validated
@RestController
@RequestMapping("/pets")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private final Map<Long, ObjectNode> petStore = new ConcurrentHashMap<>();
    private final Map<Long, Set<Long>> userFavorites = new ConcurrentHashMap<>();
    private long petIdSequence = 1000L;

    // processPet orchestrates workflow only
    private Function<ObjectNode, CompletableFuture<ObjectNode>> processPet = entity -> 
        processNormalizeStatus(entity)
        .thenCompose(this::processAddAuditRecord);

    // Normalize status attribute to lowercase
    private CompletableFuture<ObjectNode> processNormalizeStatus(ObjectNode entity) {
        if (entity.has("status") && !entity.get("status").isNull()) {
            String status = entity.get("status").asText();
            entity.put("status", status.toLowerCase(Locale.ROOT));
            logger.info("Normalized status to lowercase: {}", status.toLowerCase(Locale.ROOT));
        }
        return CompletableFuture.completedFuture(entity);
    }

    // Add audit record asynchronously to a different entity model 'PetAudit'
    private CompletableFuture<ObjectNode> processAddAuditRecord(ObjectNode entity) {
        ObjectNode auditEntity = objectMapper.createObjectNode();
        if (entity.has("technicalId") && !entity.get("technicalId").isNull()) {
            auditEntity.put("petId", entity.get("technicalId").asText());
        } else {
            auditEntity.put("petId", "unknown");
        }
        auditEntity.put("action", "CREATE_OR_UPDATE");
        auditEntity.put("timestamp", System.currentTimeMillis());

        // TODO: Replace with real entityService.addItem call, currently mock fire-and-forget
        logger.info("Adding audit record asynchronously for petId {}", auditEntity.get("petId").asText());
        // Simulate async completion:
        return CompletableFuture.completedFuture(entity);
    }
    
    // Other existing API methods here, unchanged but adapted to use ObjectNode where relevant...
    // For brevity, not included here but should follow the same pattern of modifying ObjectNode directly.

}