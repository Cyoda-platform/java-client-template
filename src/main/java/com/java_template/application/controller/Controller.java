package com.java_template.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Mail;
import com.java_template.common.service.EntityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/mail")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @PostMapping
    public ResponseEntity<Map<String, String>> createMail(@Valid @RequestBody Mail mailRequest) throws JsonProcessingException {
        try {
            if (mailRequest == null || mailRequest.getMailList() == null || mailRequest.getMailList().isEmpty() || mailRequest.getContent() == null || mailRequest.getContent().isBlank()) {
                log.error("Invalid mail creation request");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Mail.ENTITY_NAME,
                    ENTITY_VERSION,
                    mailRequest
            );

            UUID technicalId = idFuture.get();

            // Fetch back created item to get the full object including technicalId field
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Mail.ENTITY_NAME,
                    ENTITY_VERSION,
                    technicalId
            );

            ObjectNode createdNode = itemFuture.get();
            Mail createdMail = null;
            if (createdNode != null) {
                createdMail = objectMapper.treeToValue(createdNode, Mail.class);
            }

            if (createdMail == null) {
                log.error("Failed to deserialize created mail");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }

            // Process mail (evaluate happiness and send accordingly)
            // processMail method extracted

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId.toString());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            log.error("Illegal argument exception during mail creation", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Exception during mail creation", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{technicalId}")
    public ResponseEntity<Mail> getMailById(@PathVariable String technicalId) throws JsonProcessingException {
        try {
            UUID technicalUUID;
            try {
                technicalUUID = UUID.fromString(technicalId);
            } catch (IllegalArgumentException e) {
                log.error("Invalid technicalId format: {}", technicalId, e);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Mail.ENTITY_NAME,
                    ENTITY_VERSION,
                    technicalUUID
            );
            ObjectNode node = itemFuture.get();

            if (node == null) {
                log.error("Mail with technicalId {} not found", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            Mail mail = objectMapper.treeToValue(node, Mail.class);
            log.info("Mail with technicalId {} retrieved", technicalId);
            return ResponseEntity.ok(mail);

        } catch (IllegalArgumentException e) {
            log.error("Illegal argument exception during mail retrieval", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Exception during mail retrieval", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}