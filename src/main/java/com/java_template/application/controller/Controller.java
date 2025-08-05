package com.java_template.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Mail;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping(path = "/mail")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> createMail(@Valid @RequestBody Mail mail) throws Exception {
        if (mail == null || !mail.isValid()) {
            logger.error("Invalid Mail entity received");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        mail.setStatus("PENDING");

        CompletableFuture<UUID> idFuture = entityService.addItem(Mail.ENTITY_NAME, ENTITY_VERSION, mail);
        UUID technicalId = idFuture.get();

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalIdStr);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        Map<String, String> response = new HashMap<>();
        response.put("technicalId", technicalIdStr);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{technicalId}")
    public ResponseEntity<Mail> getMailById(@PathVariable String technicalId) throws JsonProcessingException, Exception {
        UUID technicalUUID = UUID.fromString(technicalId);
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Mail.ENTITY_NAME, ENTITY_VERSION, technicalUUID);
        ObjectNode node = itemFuture.get();

        if (node == null || node.isEmpty()) {
            logger.error("Mail entity not found for technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        Mail mail = objectMapper.treeToValue(node, Mail.class);
        return ResponseEntity.ok(mail);
    }
}