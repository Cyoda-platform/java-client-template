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

    @PostMapping("/mails")
    public ResponseEntity<?> createMail(@Valid @RequestBody Mail mailRequest) {
        try {
            if (mailRequest == null || mailRequest.getMailList() == null || mailRequest.getMailList().isEmpty()) {
                logger.error("Invalid mail creation request: mailList is required");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "mailList is required and cannot be empty"));
            }

            Mail mail = new Mail();
            mail.setMailList(mailRequest.getMailList());
            mail.setIsHappy(null); // will be set by criteria processing
            mail.setStatus("CREATED");

            CompletableFuture<UUID> idFuture = entityService.addItem("Mail", ENTITY_VERSION, mail);
            UUID technicalId = idFuture.join();

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId.toString()));
        } catch (IllegalArgumentException e) {
            logger.error("Illegal argument: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error creating mail: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping("/mails/{technicalId}")
    public ResponseEntity<?> getMailById(@PathVariable String technicalId) throws JsonProcessingException {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem("Mail", ENTITY_VERSION, id);
            ObjectNode itemNode = itemFuture.join();
            if (itemNode == null || itemNode.isEmpty()) {
                logger.error("Mail not found for technicalId: {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Mail not found"));
            }
            Mail mail = objectMapper.treeToValue(itemNode, Mail.class);
            return ResponseEntity.ok(mail);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid technicalId format: {}", technicalId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid technicalId format"));
        } catch (JsonProcessingException e) {
            logger.error("Error processing JSON: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Error retrieving mail: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }
}